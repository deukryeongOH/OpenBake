#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# 쓰기 경로까지 예열한 뒤 재는 측정. 구성 A/B 비교용.
#
# run-<N>.sh 의 예열은 GET /drops/{id}/info 300회뿐이다(_common.sh warmup).
# 정작 재려는 confirm-entry / lock-start 는 한 번도 실행되지 않은 채 k6 가
# 시작하므로, 측정 구간이 그대로 JIT 컴파일 구간이 된다. 2026-08-25 실측에서
# 요청당 CPU 가 예열 GET 15.4ms -> confirm 14.2ms -> lock 11.4ms 로 측정 내내
# 계속 내려갔다(문서의 예열된 베이스라인은 9.7ms). 구성을 바꿔가며 비교할 때
# 이 차이가 구성 차이보다 커서 결론이 뒤집힌다.
#
# TodayDropCache 는 당일 드롭을 리스트로 들고 기동 시 refresh 한다. 그래서
# 드롭 두 개를 먼저 만들어두고 롤아웃을 한 번만 하면 둘 다 캐시에 들어온다.
# 첫 드롭으로 쓰기 경로를 데우고, 재기동 없이 두 번째 드롭에서 측정한다.
#
#   드롭 A·B 생성 -> 롤아웃 1회 -> 대기+조회 예열
#     -> A 에 confirm+lock (버림, 쓰기 경로 예열)
#     -> B 에 confirm+lock (측정)
#
# 사용법:
#   ./warm-measure.sh <라벨> [사용자수] [재고]
#   ./warm-measure.sh pool20          # 결과: results/loadtest/warm-pool20/
#   ./warm-measure.sh pool10
#
# 전제:
#   - 서버(EC2, backend 가 뜬 노드)에서 실행한다. k6 도 같은 호스트에서 돈다.
#   - CORE_BASE_URL 을 넘기면 그 주소로 때린다. 베이스라인과 같은 경로로 재려면
#     게이트웨이 port-forward 를 쓴다:
#       kubectl -n openbake port-forward svc/api-gateway 18080:8080 &
#       CORE_BASE_URL=http://127.0.0.1:18080 ./warm-measure.sh pool20
#   - 누적 카운터 수집(poll-backend.py)은 cgroup 을 읽어야 해서 sudo 가 필요하다.
#     통과하지 못하면 건너뛰고 k6/샘플러 결과만 남긴다.
# ---------------------------------------------------------------------------
set -Eeuo pipefail

LABEL="${1:?라벨을 넘기세요. 예: ./warm-measure.sh pool20}"
USERS="${2:-300}"
STOCK="${3:-300}"

# 두 인스턴스가 동시에 돌면 같은 로그와 결과 디렉터리에 뒤섞여 쓰고, 한쪽이
# 죽으면서 다른 쪽 폴러까지 정리해 버린다(2026-08-25 실측). 한 번에 하나만 돈다.
exec 9>/tmp/warm-measure.lock
if ! flock -n 9; then
  echo "ERROR: warm-measure.sh 가 이미 실행 중입니다. 끝난 뒤 다시 실행하세요." >&2
  echo "       확인: ps -ef | grep -v grep | grep warm-measure.sh" >&2
  exit 1
fi

LOADTEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PERF_DIR="$(cd "$LOADTEST_DIR/.." && pwd)"

# _common.sh 가 ${RESULTS_ROOT:-기본값} 으로 받으므로 source 전에 정해둔다.
# 재기동 직후 JVM 이 자리잡는 시간. _common.sh 기본은 120초다.
# 뒤에 버리는 부하로 쓰기 경로를 데우므로 그만큼 놀릴 필요는 없으나, 30초로
# 줄였더니 2026-08-25 실측에서 측정 p95 가 3.5s -> 8.0s 로 나빠졌다. 기동 직후
# 백그라운드 컴파일과 캐시 적재가 아직 도는 중이라 버리는 부하까지 그 영향을
# 받는다. 60초를 절충값으로 둔다. 측정 구간의 요청당 CPU 가 계속 내려가면
# (poll-report.py 로 확인) 이 값을 다시 올려야 한다.
export PERF_WARMUP_SECONDS="${PERF_WARMUP_SECONDS:-60}"
# 계정 생성 동시성. README 경고대로 너무 올리면 member-service 가 죽는다.
export ACCOUNT_WORKERS="${ACCOUNT_WORKERS:-16}"
# 토큰이 이만큼 이상 남아 있으면 재발급하지 않는다(300명 재발급에 7분이 든다).
MIN_TOKEN_MINUTES="${MIN_TOKEN_MINUTES:-15}"

RESULTS_ROOT="${RESULTS_ROOT:-$PERF_DIR/results/loadtest/warm-$LABEL}"
export RESULTS_ROOT
mkdir -p "$RESULTS_ROOT"

# 계정 생성 / 드롭 생성 / 롤아웃 / 조회 예열 / run_scenario 를 그대로 쓴다.
source "$LOADTEST_DIR/_common.sh"

POLL_CSV="$RESULTS_ROOT/poll.csv"

# PREP_ONLY=true 면 계정·드롭·롤아웃·조회 예열까지만 하고 멈춘다. k6 를 서버가 아닌
# 다른 호스트(맥)에서 돌릴 때 쓴다. 부하 생성기가 측정 대상과 같은 2코어 노드에서
# 도는 것이 남은 왜곡 중 가장 크기 때문이다. 폴러도 띄우지 않는다 — 이 스크립트가
# 끝나면서 trap 이 정리해 버리므로, 부하를 거는 쪽에서 따로 띄운다.
PREP_ONLY="${PREP_ONLY:-false}"

start_poller() {
  [[ "$PREP_ONLY" == "true" ]] && return 0
  if ! sudo -n true 2>/dev/null; then
    warn "sudo 가 통하지 않아 누적 카운터 수집을 건너뜁니다(요청당 CPU·acquire 평균 없음)."
    return
  fi
  sudo -n env "KUBECONFIG=${KUBECONFIG:-$HOME/.kube/config}" \
    python3 "$LOADTEST_DIR/poll-backend.py" "$POLL_CSV" \
    > "$RESULTS_ROOT/poll.log" 2>&1 < /dev/null &
  POLLER_PID=$!
  echo "  누적 카운터 수집 시작: $POLL_CSV (pid $POLLER_PID)"
}

stop_poller() {
  # PID 로만 정리한다. pkill -f 패턴은 동시에 도는 다른 실행의 폴러까지 잡는다.
  [[ -n "${POLLER_PID:-}" ]] || return 0
  sudo -n kill "$POLLER_PID" 2>/dev/null || true
  POLLER_PID=""
}

# users 파일 첫 토큰의 exp 까지 남은 분. 파일이 없거나 못 읽으면 -1.
token_minutes_left() {
  python3 - "$1" <<'EOF_TOKEN'
import base64, json, sys, time
try:
    users = json.load(open(sys.argv[1], encoding="utf-8"))
    payload = users[0]["token"].split(".")[1]
    payload += "=" * ((4 - len(payload) % 4) % 4)
    exp = json.loads(base64.urlsafe_b64decode(payload))["exp"]
    print(int((exp - time.time()) // 60))
except Exception:
    print(-1)
EOF_TOKEN
}

# 드롭 하나를 만들고 dropId 를 stdout 으로 돌려준다.
# PERF_RESTART_BACKEND=false 로 두어 드롭마다 재기동하지 않게 한다. 롤아웃은
# 두 드롭을 모두 만든 뒤 한 번만 돌린다.
make_drop() {
  local tag="$1" out="$RESULTS_ROOT/prepare-$1.txt"
  (
    cd "$PREPARE_DIR"
    PERF_DROP_STOCK="$STOCK" \
    PERF_RESTART_BACKEND=false \
    PERF_EXEC_MODE="${PERF_EXEC_MODE:-kubectl}" \
    PERF_K8S_NAMESPACE="$NAMESPACE" \
    PERF_DB_WORKLOAD="${PERF_DB_WORKLOAD:-statefulset/core-postgres}" \
    PERF_BACKEND_WORKLOAD="deployment/$DEPLOYMENT" \
    QUANTITY="$QUANTITY" \
    python3 perf-data.py "$PROFILE" drop "$USERS"
  ) > "$out" 2>&1 || { cat "$out" >&2; return 1; }
  grep -E '^dropId' "$out" | tail -1 | tr -d ' ' | cut -d: -f2
}

# 예열용 k6. 판정도 샘플링도 하지 않는다. 결과는 버린다.
burn_in() {
  local script="$1" drop_id="$2" expected_success="$3" expected_sold_out="$4" name="$5"
  echo "  예열 부하: $name (drop $drop_id)"
  k6 run --quiet \
    -e "CORE_BASE_URL=$CORE_BASE_URL" \
    -e "DROP_ID=$drop_id" \
    -e "USER_COUNT=$USERS" \
    -e "QUANTITY=$QUANTITY" \
    -e "EXPECTED_SUCCESS=$expected_success" \
    -e "EXPECTED_SOLD_OUT=$expected_sold_out" \
    -e "AUTH_MODE=${AUTH_MODE:-gateway}" \
    -e "HTTP_TIMEOUT=$HTTP_TIMEOUT" \
    -e "MAX_DURATION=$MAX_DURATION" \
    "$PERF_DIR/$script" > "$RESULTS_ROOT/burnin-$name.txt" 2>&1 \
    || warn "예열 부하 $name 이 임계값을 넘겼습니다(예열이므로 무시)."
}

trap stop_poller EXIT

require k6
require python3
require curl
resolve_base_url

cat <<EOF_HEAD

========================================================
 예열 측정 — $LABEL   (사용자 ${USERS}명 / 재고 ${STOCK}개)
========================================================
 대상      : $CORE_BASE_URL
 결과 경로 : $RESULTS_ROOT
 절차      : 드롭 2개 -> 롤아웃 1회 -> 조회 예열 -> A 버림 -> B 측정
========================================================
EOF_HEAD

log "구성 확인"
$KUBECTL -n "$NAMESPACE" get deploy "$DEPLOYMENT" \
  -o jsonpath='  request : {.spec.template.spec.containers[0].resources.requests}{"\n"}  env     : {range .spec.template.spec.containers[0].env[*]}{.name}={.value} {end}{"\n"}'
$KUBECTL -n "$NAMESPACE" get hpa "$HPA_NAME" 2>/dev/null || echo "  hpa     : 없음"

# --- 1) 계정 + 토큰 ---
# 300명 재발급은 로그인 300회라 7분 가까이 걸린다(2026-08-25 실측). 토큰 수명은
# 30분이므로 직전 실행 것이 아직 넉넉하면 그대로 쓴다. 부족하면 다시 받는다.
USERS_FILE="$PERF_DIR/users-${USERS}.json"
LEFT="$(token_minutes_left "$USERS_FILE")"
if [[ "${REUSE_TOKENS:-true}" == "true" ]] && (( LEFT >= MIN_TOKEN_MINUTES )); then
  log "토큰 재사용 (${LEFT}분 남음, 기준 ${MIN_TOKEN_MINUTES}분)"
else
  log "계정 ${USERS}명 + JWT 발급 (남은 시간 ${LEFT}분, 동시성 ${ACCOUNT_WORKERS})"
  python3 "$LOADTEST_DIR/create-users.py" --count "$USERS" --profile "$PROFILE" \
    --workers "$ACCOUNT_WORKERS" --output "$USERS_FILE"
fi
cp "$USERS_FILE" "$PERF_DIR/users.json"

# --- 2) 드롭 두 개 ---
log "드롭 2개 생성 (예열용 A, 측정용 B / 각 재고 ${STOCK})"
# make_drop 안의 die 는 $( ) 서브셸만 끝낸다. 부모가 그걸 모르고 계속 가면
# 빈 dropId 로 측정을 돌게 되므로 여기서 종료 코드와 값을 모두 확인한다.
DROP_A="$(make_drop a)" || die "드롭 생성 실패(A). $RESULTS_ROOT/prepare-a.txt 확인"
DROP_B="$(make_drop b)" || die "드롭 생성 실패(B). $RESULTS_ROOT/prepare-b.txt 확인"
[[ -n "$DROP_A" && -n "$DROP_B" ]] || die "dropId 를 찾지 못했습니다. $RESULTS_ROOT/prepare-*.txt 확인"
echo "  예열용 A = $DROP_A / 측정용 B = $DROP_B"

start_poller

# --- 3) 롤아웃 한 번 (두 드롭이 함께 TodayDropCache 에 올라온다) ---
rollout_backend

# --- 4) 조회 경로 예열 ---
warmup "$DROP_B"

if [[ "$PREP_ONLY" == "true" ]]; then
  cat <<EOF_PREP

========================================================
 준비 완료 — k6 는 다른 호스트에서 돌린다
========================================================
 예열용 드롭 A : $DROP_A
 측정용 드롭 B : $DROP_B
 계정 파일     : $PERF_DIR/users-${USERS}.json
========================================================
EOF_PREP
  exit 0
fi

# --- 5) 쓰기 경로 예열 — 결과는 버린다 ---
log "쓰기 경로 예열 (drop $DROP_A, 결과 버림)"
burn_in "drop-confirm-entry-concurrency.js" "$DROP_A" "$USERS" 0 confirm
burn_in "drop-lock-concurrency.js" "$DROP_A" \
  "$(( USERS < STOCK ? USERS : STOCK ))" "$(( USERS - (USERS < STOCK ? USERS : STOCK) ))" lock
echo "  예열 후 안정화 10초"
sleep 10

# --- 6) 측정 — drop B ---
EXPECTED_SUCCESS=$(( USERS < STOCK ? USERS : STOCK ))
EXPECTED_SOLD_OUT=$(( USERS - EXPECTED_SUCCESS ))

log "측정 (drop $DROP_B)"
run_scenario "confirm" "drop-confirm-entry-concurrency.js" \
  "$USERS" "$DROP_B" "$USERS" 0 "$STOCK"
run_scenario "lock" "drop-lock-concurrency.js" \
  "$USERS" "$DROP_B" "$EXPECTED_SUCCESS" "$EXPECTED_SOLD_OUT" "$STOCK"

stop_poller

cat <<EOF_TAIL

========================================================
 완료 — $LABEL   (예열 drop $DROP_A / 측정 drop $DROP_B)
========================================================
 결과      : $RESULTS_ROOT
 누적 카운터: $POLL_CSV
   구간 지표는 아래로 뽑는다.
   python3 $LOADTEST_DIR/poll-report.py $POLL_CSV
========================================================
EOF_TAIL
