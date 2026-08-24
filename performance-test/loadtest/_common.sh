#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# 티어별 부하 테스트 공통 실행 로직.
#
# run-100.sh / run-300.sh / run-500.sh / run-1000.sh 가 사용자 수와 재고만 정하고
# 이 파일의 run_tier 를 호출한다. 티어마다 파일을 따로 두되 절차는 한 곳에 모아
# 한쪽만 고쳐지는 일을 막는다.
#
# 절차
#   1) 계정 생성 + JWT 발급        create-users.py  -> users-<N>.json
#   2) 드롭 생성 (재고 지정)        prepare/perf-data.py
#   3) backend 롤아웃              TodayDropCache 갱신 + 전 Pod Ready 대기
#   4) users.json 교체             k6-users.js 가 항상 ./users.json 을 읽는다
#   5) confirm-entry 부하          지표 샘플링 동시 실행 -> 판정/진단
#   6) lock 부하                   지표 샘플링 동시 실행 -> 판정/진단
#
# k6 는 run-k6.sh 를 거치지 않고 직접 호출한다.
# run-k6.sh 는 `set -a; source .env.k6.<profile>` 로 호출자가 넘긴 USER_COUNT/DROP_ID 를
# 덮어써 버리기 때문에 티어별 값 주입이 통하지 않는다.
# ---------------------------------------------------------------------------
set -Eeuo pipefail

LOADTEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PERF_DIR="$(cd "$LOADTEST_DIR/.." && pwd)"
PREPARE_DIR="$PERF_DIR/prepare"

PROFILE="${PERF_PROFILE:-server}"
NAMESPACE="${PERF_K8S_NAMESPACE:-openbake}"
DEPLOYMENT="${PERF_BACKEND_DEPLOYMENT:-backend}"
HPA_NAME="${PERF_BACKEND_HPA:-backend}"
KUBECTL="${PERF_KUBECTL:-kubectl}"

# startupProbe 가 failureThreshold 48 x period 5s = 240초까지 기다린다.
# perf-data.py 의 rollout status 는 120초로 고정돼 있어 그대로 쓰면 중간에 죽는다.
# 그래서 재기동은 PERF_RESTART_BACKEND=false 로 끄고 여기서 직접 한다.
ROLLOUT_TIMEOUT="${PERF_ROLLOUT_TIMEOUT:-420s}"

# 재기동 직후에는 JIT 미예열로 응답시간이 실제보다 크게 나온다.
WARMUP_SECONDS="${PERF_WARMUP_SECONDS:-120}"
WARMUP_REQUESTS="${PERF_WARMUP_REQUESTS:-300}"

SAMPLE_INTERVAL="${SAMPLE_INTERVAL:-1}"
HTTP_TIMEOUT="${HTTP_TIMEOUT:-30s}"
MAX_DURATION="${MAX_DURATION:-300s}"
QUANTITY="${QUANTITY:-1}"

RESULTS_ROOT="${RESULTS_ROOT:-$PERF_DIR/results/loadtest}"
NFR_LOG="${NFR_LOG:-$RESULTS_ROOT/nfr-log.md}"

log()  { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[33mWARN: %s\033[0m\n' "$*" >&2; }
die()  { printf '\033[31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

require() {
  command -v "$1" >/dev/null 2>&1 || die "$1 명령이 필요합니다."
}

resolve_base_url() {
  # server 프로파일은 SERVER_BASE_URL 하나로 게이트웨이(Traefik Ingress)를 가리킨다.
  local env_file="$PREPARE_DIR/.env.$PROFILE"
  if [[ -z "${CORE_BASE_URL:-}" && -f "$env_file" ]]; then
    CORE_BASE_URL="$(grep -E '^SERVER_BASE_URL=' "$env_file" | tail -1 | cut -d= -f2- || true)"
  fi
  [[ -n "${CORE_BASE_URL:-}" ]] || die "CORE_BASE_URL 을 정하지 못했습니다. \
$env_file 의 SERVER_BASE_URL 을 설정하거나 CORE_BASE_URL 을 직접 export 하세요."
  CORE_BASE_URL="${CORE_BASE_URL%/}"
}

rollout_backend() {
  log "backend 롤아웃 (TodayDropCache 갱신)"
  $KUBECTL -n "$NAMESPACE" rollout restart "deployment/$DEPLOYMENT"
  # rollout status 는 신규 Pod 가 전부 Ready 가 될 때까지 블로킹한다.
  # replica 가 2개인 상태에서 일부만 갱신되면 같은 드롭인데 Pod 마다 캐시가 달라져
  # 요청이 어디로 가느냐에 따라 결과가 바뀐다. 그래서 완료를 반드시 기다린다.
  $KUBECTL -n "$NAMESPACE" rollout status "deployment/$DEPLOYMENT" \
    --timeout="$ROLLOUT_TIMEOUT"
  $KUBECTL -n "$NAMESPACE" get pods -l "app.kubernetes.io/name=$DEPLOYMENT" -o wide
}

warmup() {
  local drop_id="$1"
  if (( WARMUP_SECONDS > 0 )); then
    log "JVM 예열 대기 ${WARMUP_SECONDS}s"
    sleep "$WARMUP_SECONDS"
  fi
  if (( WARMUP_REQUESTS > 0 )); then
    log "조회 경로로 예열 (${WARMUP_REQUESTS}회, 상태 변경 없음)"
    local i
    for ((i = 0; i < WARMUP_REQUESTS; i++)); do
      curl -sk -o /dev/null "$CORE_BASE_URL/api/v1/drops/$drop_id/info" || true
    done
    curl -sk -o /dev/null -w '  예열 후 1건: %{time_total}s\n' \
      "$CORE_BASE_URL/api/v1/drops/$drop_id/info" || true
  fi
}

# k6 실행 + 지표 샘플링 + 판정을 한 묶음으로 처리한다.
#   $1 scenario (confirm|lock)
#   $2 k6 스크립트
#   $3 users
#   $4 drop_id
#   $5 expected_success
#   $6 expected_sold_out
#   $7 stock
run_scenario() {
  local scenario="$1" script="$2" users="$3" drop_id="$4"
  local expected_success="$5" expected_sold_out="$6" stock="$7"

  local ts run_dir
  ts="$(date -u +%Y%m%dT%H%M%SZ)"
  run_dir="$RESULTS_ROOT/${ts}-${scenario}-u${users}-drop${drop_id}"
  mkdir -p "$run_dir"

  log "$scenario 부하 테스트 (사용자 ${users}명)"

  # 샘플러를 먼저 띄운다. port-forward 가 붙을 시간이 필요해서 k6 보다 앞서 시작한다.
  python3 "$LOADTEST_DIR/sample-backend.py" \
    --out "$run_dir/sample.csv" \
    --namespace "$NAMESPACE" \
    --deployment "$DEPLOYMENT" \
    --hpa "$HPA_NAME" \
    --interval "$SAMPLE_INTERVAL" &
  local sampler_pid=$!
  sleep 4

  local k6_status=0
  set +e
  k6 run \
    --summary-export "$run_dir/summary.json" \
    --tag "testid=${ts}-${scenario}-u${users}" \
    -e "CORE_BASE_URL=$CORE_BASE_URL" \
    -e "DROP_ID=$drop_id" \
    -e "USER_COUNT=$users" \
    -e "QUANTITY=$QUANTITY" \
    -e "EXPECTED_SUCCESS=$expected_success" \
    -e "EXPECTED_SOLD_OUT=$expected_sold_out" \
    -e "AUTH_MODE=${AUTH_MODE:-gateway}" \
    -e "HTTP_TIMEOUT=$HTTP_TIMEOUT" \
    -e "MAX_DURATION=$MAX_DURATION" \
    "$PERF_DIR/$script" 2>&1 | tee "$run_dir/console.txt"
  k6_status=${PIPESTATUS[0]}
  set -e

  # 버스트가 끝난 직후의 자원 상태(GC, 스레드 반납)까지 담는다.
  sleep 5
  kill -TERM "$sampler_pid" 2>/dev/null || true
  wait "$sampler_pid" 2>/dev/null || true

  cat > "$run_dir/metadata.env" <<EOF_META
scenario=$scenario
profile=$PROFILE
users=$users
stock=$stock
drop_id=$drop_id
expected_success=$expected_success
expected_sold_out=$expected_sold_out
core_base_url=$CORE_BASE_URL
k6_exit=$k6_status
EOF_META

  log "$scenario 판정 및 진단"
  local diag_status=0
  set +e
  python3 "$LOADTEST_DIR/diagnose.py" \
    --summary "$run_dir/summary.json" \
    --sample "$run_dir/sample.csv" \
    --scenario "$scenario" \
    --users "$users" \
    --stock "$stock" \
    --drop-id "$drop_id" \
    --expected-success "$expected_success" \
    --expected-sold-out "$expected_sold_out" \
    --out "$run_dir/diagnosis.txt" \
    --log "$NFR_LOG"
  diag_status=$?
  set -e

  echo "  결과: $run_dir"
  if (( diag_status != 0 )); then
    warn "$scenario 가 NFR 기준(P95 1.5s / P99 3s)을 넘겼습니다. 진단 내용은 위와 $NFR_LOG 참고."
  fi
  return 0
}

# ---------------------------------------------------------------------------
# run_tier <users> <stock>
# ---------------------------------------------------------------------------
run_tier() {
  local users="$1" stock="$2"

  require k6
  require python3
  require curl
  command -v "$KUBECTL" >/dev/null 2>&1 || die "$KUBECTL 명령이 필요합니다."

  local expected_success=$(( users < stock ? users : stock ))
  local expected_sold_out=$(( users - expected_success ))

  resolve_base_url
  mkdir -p "$RESULTS_ROOT"

  cat <<EOF_HEAD

========================================================
 OpenBake 부하 테스트 — 사용자 ${users}명 / 재고 ${stock}개
========================================================
 대상       : $CORE_BASE_URL
 네임스페이스: $NAMESPACE   (deployment/$DEPLOYMENT, hpa/$HPA_NAME)
 기대 결과  : 선점 성공 ${expected_success} / 품절 ${expected_sold_out}
 NFR        : P95 <= 1500ms, P99 <= 3000ms
 결과 경로  : $RESULTS_ROOT
========================================================
EOF_HEAD

  # --- 1) 계정 ---
  local users_file="$PERF_DIR/users-${users}.json"
  if [[ -f "$users_file" && "${REUSE_USERS:-true}" == "true" ]]; then
    log "계정 재사용: $users_file (새로 만들려면 REUSE_USERS=false)"
    # JWT 는 만료될 수 있으므로 토큰만 다시 받는다.
    python3 "$LOADTEST_DIR/create-users.py" --count "$users" --profile "$PROFILE" \
      --output "$users_file"
  else
    log "계정 ${users}명 생성 + JWT 발급"
    python3 "$LOADTEST_DIR/create-users.py" --count "$users" --profile "$PROFILE" \
      --output "$users_file"
  fi

  local actual
  actual="$(python3 -c "import json,sys; print(len(json.load(open(sys.argv[1],encoding='utf-8'))))" "$users_file")"
  (( actual >= users )) || die "계정이 부족합니다. 필요 ${users}, 실제 ${actual}"

  # --- 2) 드롭 ---
  log "드롭 생성 (재고 ${stock}개)"
  (
    cd "$PREPARE_DIR"
    PERF_DROP_STOCK="$stock" \
    PERF_RESTART_BACKEND=false \
    PERF_EXEC_MODE="${PERF_EXEC_MODE:-kubectl}" \
    PERF_K8S_NAMESPACE="$NAMESPACE" \
    PERF_DB_WORKLOAD="${PERF_DB_WORKLOAD:-statefulset/core-postgres}" \
    PERF_BACKEND_WORKLOAD="deployment/$DEPLOYMENT" \
    QUANTITY="$QUANTITY" \
    python3 perf-data.py "$PROFILE" drop "$users"
  ) | tee "$RESULTS_ROOT/last-prepare.txt"

  local drop_id
  drop_id="$(grep -E '^dropId' "$RESULTS_ROOT/last-prepare.txt" | tail -1 | tr -d ' ' | cut -d: -f2)"
  [[ -n "$drop_id" ]] || die "생성된 dropId 를 찾지 못했습니다. $RESULTS_ROOT/last-prepare.txt 확인"
  echo "  dropId=$drop_id"

  # --- 3) 롤아웃 + 예열 ---
  rollout_backend
  warmup "$drop_id"

  # --- 4) users.json 교체 ---
  # k6-users.js 가 항상 ./users.json 을 읽으므로 티어 파일을 복사해서 쓴다.
  cp "$users_file" "$PERF_DIR/users.json"
  log "users.json <- users-${users}.json (${actual}명)"

  # --- 5) confirm-entry ---
  # lock-start 는 entryStatus='ENTERED' 를 요구하므로 반드시 선행해야 한다.
  # 건너뛰면 전원이 DR014 로 떨어져 재고를 건드리지도 못한 채 통과해 버린다.
  run_scenario "confirm" "drop-confirm-entry-concurrency.js" \
    "$users" "$drop_id" "$users" 0 "$stock"

  # --- 6) lock ---
  run_scenario "lock" "drop-lock-concurrency.js" \
    "$users" "$drop_id" "$expected_success" "$expected_sold_out" "$stock"

  cat <<EOF_TAIL

========================================================
 완료 — 사용자 ${users}명 / 재고 ${stock}개  (drop ${drop_id})
========================================================
 판정 로그 : $NFR_LOG
 실행 결과 : $RESULTS_ROOT
========================================================
EOF_TAIL
}