#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [[ -f ".env.k6" ]]; then
  set -a
  # shellcheck disable=SC1091
  source ".env.k6"
  set +a
fi

run_lock() {
  local results_root="${RESULTS_ROOT:-$SCRIPT_DIR/results/runs}"
  local ts run_id run_dir start_time end_time status
  ts="$(date -u +%Y%m%dT%H%M%SZ)"
  run_id="${ts}-lock-concurrency-u${USER_COUNT}-drop${DROP_ID}"
  run_dir="$results_root/$run_id"
  mkdir -p "$run_dir"

  start_time="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  set +e
  k6 run --summary-export "$run_dir/summary.json" drop-lock-concurrency.js 2>&1 | tee "$run_dir/console.txt"
  status=${PIPESTATUS[0]}
  set -e
  end_time="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

  if (( status == 0 )); then result=PASS; else result=FAIL; fi
  cat > "$run_dir/metadata.env" <<EOF
run_id=$run_id
scenario=stock-concurrency
user_count=${USER_COUNT}
drop_id=${DROP_ID}
quantity=${QUANTITY}
expected_success=${EXPECTED_SUCCESS:-$USER_COUNT}
expected_sold_out=${EXPECTED_SOLD_OUT:-0}
start_time=$start_time
end_time=$end_time
result=$result
EOF
  echo "result_dir=$run_dir"
  return "$status"
}

CMD="${1:-}"
case "$CMD" in
  users) python3 generate-user-json.py ;;
  enter) k6 run drop-enter-concurrency.js ;;
  wait-active) k6 run drop-wait-active.js ;;
  confirm) k6 run drop-confirm-entry-concurrency.js ;;
  lock) run_lock ;;
  *)
    echo "사용법: ./run-k6.sh {users|enter|wait-active|confirm|lock}"
    exit 1
    ;;
esac
