#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

PROFILE="${1:-}"
CMD="${2:-}"

usage() {
  echo "사용법: ./run-k6.sh {local|server} {users|flow|confirm|lock|oversell}"
}

if [[ "$PROFILE" != "local" && "$PROFILE" != "server" ]]; then
  usage
  exit 1
fi

if [[ -z "$CMD" ]]; then
  usage
  exit 1
fi

ENV_FILE=".env.k6.${PROFILE}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "ERROR: $ENV_FILE 파일이 없습니다."
  exit 1
fi

# CLI에서 USER_COUNT=300 ./run-k6.sh server lock 처럼 전달한 값은
# .env.k6보다 우선하도록 보존한다. Capacity scan이 이 동작에 의존한다(각 CSV 행의 독립 Drop 전제).
# declare -A(bash 4+)는 macOS 기본 bash 3.2에서 동작하지 않으므로 병렬 배열로 구현한다.
OVERRIDE_VARS=(
  CORE_BASE_URL MEMBER_BASE_URL DROP_ID USER_COUNT START_INDEX QUANTITY
  EXPECTED_SUCCESS EXPECTED_SOLD_OUT LOGIN_PATH TEST_PASSWORD TOKEN_PATH
  EMAIL_FIELD PASSWORD_FIELD EMAIL_PREFIX EMAIL_DOMAIN OUTPUT_FILE REQUEST_TIMEOUT
  K6_PROMETHEUS_RW_ENABLED K6_PROMETHEUS_RW_SERVER_URL
  K6_PROMETHEUS_RW_TREND_STATS K6_PROMETHEUS_RW_PUSH_INTERVAL
)
CALLER_OVERRIDE_NAMES=()
CALLER_OVERRIDE_VALUES=()
for var in "${OVERRIDE_VARS[@]}"; do
  if [[ -n "${!var+x}" ]]; then
    CALLER_OVERRIDE_NAMES+=("$var")
    CALLER_OVERRIDE_VALUES+=("${!var}")
  fi
done

set -a
source "$ENV_FILE"
set +a

for i in "${!CALLER_OVERRIDE_NAMES[@]}"; do
  printf -v "${CALLER_OVERRIDE_NAMES[$i]}" '%s' "${CALLER_OVERRIDE_VALUES[$i]}"
  export "${CALLER_OVERRIDE_NAMES[$i]}"
done

echo "======================================"
echo "Profile         : $PROFILE"
echo "CORE_BASE_URL   : $CORE_BASE_URL"
echo "MEMBER_BASE_URL : $MEMBER_BASE_URL"
echo "USER_COUNT      : $USER_COUNT"
echo "DROP_ID         : $DROP_ID"
echo "======================================"

run_k6() {
  local scenario="$1"
  local script="$2"
  local results_root="${RESULTS_ROOT:-$SCRIPT_DIR/results/runs}"
  local ts run_id run_dir start_time end_time status result
  local -a k6_args

  ts="$(date -u +%Y%m%dT%H%M%SZ)"
  run_id="${ts}-${scenario}-u${USER_COUNT}-drop${DROP_ID}"
  run_dir="$results_root/$run_id"
  mkdir -p "$run_dir"

  k6_args=(run --summary-export "$run_dir/summary.json" --tag "testid=$run_id")

  if [[ "${K6_PROMETHEUS_RW_ENABLED:-false}" == "true" ]]; then
    k6_args+=(-o experimental-prometheus-rw)
  fi

  start_time="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  set +e
  k6 "${k6_args[@]}" "$script" 2>&1 | tee "$run_dir/console.txt"
  status=${PIPESTATUS[0]}
  set -e
  end_time="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

  if (( status == 0 )); then
    result="PASS"
  else
    result="FAIL"
  fi

  cat > "$run_dir/metadata.env" <<EOF_META
run_id=$run_id
scenario=$scenario
profile=$PROFILE
user_count=${USER_COUNT}
drop_id=${DROP_ID}
quantity=${QUANTITY:-}
expected_success=${EXPECTED_SUCCESS:-}
expected_sold_out=${EXPECTED_SOLD_OUT:-}
start_time=$start_time
end_time=$end_time
result=$result
EOF_META

  echo "result_dir=$run_dir"
  return "$status"
}

case "$CMD" in
  users)
    python3 generate-user-json.py
    ;;
  flow)
      run_k6 "drop-user-flow" "drop-user-flow.js"
      ;;
  confirm)
    run_k6 "drop-confirm-entry-concurrency" "drop-confirm-entry-concurrency.js"
    ;;
  lock)
    run_k6 "drop-lock-concurrency" "drop-lock-concurrency.js"
    ;;
  oversell)
    run_k6 "drop-oversell-verification" "drop-oversell-verification.js"
    ;;
  *)
    usage
    exit 1
    ;;
esac
