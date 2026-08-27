#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# k6 만 이 호스트(맥)에서 돌린다. 준비는 서버의 warm-measure.sh PREP_ONLY 가 한다.
#
# 왜 나누는가
#   k6 를 서버에서 돌리면 부하 생성기가 측정 대상과 같은 2 vCPU 노드에서 돈다.
#   2026-08-25 실측에서 부하 구간 system_cpu 가 0.90 이었는데 그중 상당 부분이
#   k6 자신이었다. backend 를 재는 게 아니라 노드 포화를 재게 된다.
#
# 대가
#   맥에서는 port-forward 를 쓸 수 없어 Traefik(공개 주소)을 타야 한다. 왕복이
#   130~200ms 늘고, 경로가 달라져 port-forward 로 잰 이전 값과 직접 비교할 수 없다.
#   대신 이쪽이 실제 프로덕션 경로다.
#
# 절차 (warm-measure.sh 와 동일하게 쓰기 경로를 먼저 데운다)
#   드롭 A 에 confirm+lock  -> 버림
#   드롭 B 에 confirm+lock  -> 측정
#
# 사용법
#   ./run-k6-local.sh <라벨> <드롭A> <드롭B> [사용자수] [재고]
#
# 전제
#   - 서버에서 PREP_ONLY=true ./warm-measure.sh 로 드롭 두 개를 만들어 두었을 것
#   - users.json 을 서버에서 받아 performance-test/users.json 에 둘 것
#     (이 스크립트가 --fetch-users 로 직접 받아온다)
# ---------------------------------------------------------------------------
set -Eeuo pipefail

LABEL="${1:?라벨}"; DROP_A="${2:?예열용 dropId}"; DROP_B="${3:?측정용 dropId}"
USERS="${4:-300}"; STOCK="${5:-300}"

LOADTEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PERF_DIR="$(cd "$LOADTEST_DIR/.." && pwd)"

BASE_URL="${CORE_BASE_URL:-https://3.38.24.67.sslip.io}"
SSH_HOST="${SSH_HOST:-ubuntu@3.38.24.67}"
REMOTE_PERF="${REMOTE_PERF:-~/beadv7_7_BakerySite6_BE/performance-test}"
RESULTS_ROOT="$PERF_DIR/results/loadtest/local-$LABEL"
HTTP_TIMEOUT="${HTTP_TIMEOUT:-30s}"
MAX_DURATION="${MAX_DURATION:-300s}"
QUANTITY="${QUANTITY:-1}"

mkdir -p "$RESULTS_ROOT"

log() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }

# 서버가 갱신한 토큰을 그대로 받아온다. 여기서 다시 발급하면 로그인 300회가
# 공개 경로로 또 나가고, 서버 쪽 users.json 과 토큰이 어긋난다.
log "계정 파일 수신"
scp -q "$SSH_HOST:$REMOTE_PERF/users-${USERS}.json" "$PERF_DIR/users.json"
python3 -c "import json,sys;d=json.load(open('$PERF_DIR/users.json'));print(f'  {len(d)}명')"

run_k6() {
  local script="$1" drop_id="$2" success="$3" sold_out="$4" out="$5" quiet="$6"
  local args=(
    -e "CORE_BASE_URL=$BASE_URL" -e "DROP_ID=$drop_id"
    -e "USER_COUNT=$USERS" -e "QUANTITY=$QUANTITY"
    -e "EXPECTED_SUCCESS=$success" -e "EXPECTED_SOLD_OUT=$sold_out"
    -e "AUTH_MODE=${AUTH_MODE:-gateway}"
    -e "HTTP_TIMEOUT=$HTTP_TIMEOUT" -e "MAX_DURATION=$MAX_DURATION"
  )
  [[ "$quiet" == "quiet" ]] && args+=(--quiet) \
    || args+=(--summary-export "$RESULTS_ROOT/$(basename "$out" .txt).json")
  k6 run "${args[@]}" "$PERF_DIR/$script" > "$out" 2>&1 || true
}

SUCCESS=$(( USERS < STOCK ? USERS : STOCK ))
SOLD_OUT=$(( USERS - SUCCESS ))

log "쓰기 경로 예열 — drop $DROP_A (결과 버림)"
run_k6 drop-confirm-entry-concurrency.js "$DROP_A" "$USERS" 0 "$RESULTS_ROOT/burnin-confirm.txt" quiet
run_k6 drop-lock-concurrency.js "$DROP_A" "$SUCCESS" "$SOLD_OUT" "$RESULTS_ROOT/burnin-lock.txt" quiet
echo "  안정화 10초"; sleep 10

log "측정 — drop $DROP_B"
run_k6 drop-confirm-entry-concurrency.js "$DROP_B" "$USERS" 0 "$RESULTS_ROOT/confirm.txt" full
sleep 5
run_k6 drop-lock-concurrency.js "$DROP_B" "$SUCCESS" "$SOLD_OUT" "$RESULTS_ROOT/lock.txt" full

log "결과"
for f in confirm lock; do
  echo "--- $f"
  grep -E 'p\(95\)=|http_reqs|checks_failed' "$RESULTS_ROOT/$f.txt" | grep -v expected_response || true
done
echo
echo "  대상   : $BASE_URL"
echo "  결과   : $RESULTS_ROOT"
