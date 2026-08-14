#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PERF_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PLAN_FILE="${1:-$SCRIPT_DIR/capacity-plan.csv}"

if [[ ! -f "$PLAN_FILE" ]]; then
    echo "ERROR: Capacity plan이 없습니다: $PLAN_FILE"
    echo "cp capacity/capacity-plan.example.csv capacity/capacity-plan.csv 후 Drop ID를 준비하세요."
    exit 1
fi

if [[ ! -x "$PERF_DIR/run-k6.sh" ]]; then
    chmod +x "$PERF_DIR/run-k6.sh"
fi

if [[ ! -f "$PERF_DIR/users.json" ]]; then
    echo "ERROR: users.json이 없습니다. 먼저 충분한 테스트 사용자를 생성하세요."
    exit 1
fi

max_users="$(awk -F, 'NR>1 && $1 ~ /^[0-9]+$/ {if ($1>m) m=$1} END {print m+0}' "$PLAN_FILE")"
actual_users="$(python3 - "$PERF_DIR/users.json" <<'PY'
import json, sys
with open(sys.argv[1], encoding='utf-8') as f:
    print(len(json.load(f)))
PY
)"

if (( actual_users < max_users )); then
    echo "ERROR: users.json 사용자 수가 부족합니다. 필요=${max_users}, 현재=${actual_users}"
    exit 1
fi

echo "========================================"
echo " OpenBake Lock Capacity Scan"
echo "========================================"
echo "Plan : $PLAN_FILE"
echo "Users: $actual_users"
echo

echo "주의: lock-start는 상태를 변경하는 API입니다."
echo "각 행의 DROP_ID는 해당 사용자들이 confirm-entry까지 완료되어 있고"
echo "재고가 기대 성공 건수 이상 준비된 독립 테스트 Drop이어야 합니다."
echo

read -r -p "사전조건을 준비했습니까? [y/N] " answer
if [[ ! "$answer" =~ ^[Yy]$ ]]; then
    echo "중단합니다."
    exit 0
fi

failed_steps=0

while IFS=, read -r users drop_id expected_success expected_sold_out; do
    [[ "$users" == "users" ]] && continue
    [[ -z "$users" ]] && continue

    if [[ "$drop_id" == "CHANGE_ME" || -z "$drop_id" ]]; then
        echo "ERROR: users=${users} 행의 drop_id를 설정하세요."
        exit 1
    fi

    echo
    echo "----------------------------------------"
    echo " Capacity step: users=${users}, drop=${drop_id}"
    echo "----------------------------------------"

    set +e
    (
        cd "$PERF_DIR"
        USER_COUNT="$users" \
        DROP_ID="$drop_id" \
        EXPECTED_SUCCESS="$expected_success" \
        EXPECTED_SOLD_OUT="$expected_sold_out" \
        ./run-k6.sh lock
    )
    step_status=$?
    set -e

    if (( step_status != 0 )); then
        echo "WARN: users=${users} step이 NFR/정합성 기준을 통과하지 못했습니다. 다음 step을 계속합니다."
        failed_steps=$((failed_steps + 1))
    fi

done < "$PLAN_FILE"

echo
echo "Capacity scan 완료. 실패 step=${failed_steps}"
echo "결과 집계:"
echo "  python3 capacity/analyze-capacity.py"

# Capacity 한계 탐색에서는 실패 step도 필요한 데이터이므로 모든 step을 실행한 뒤 종료합니다.
# CI에서 실패 여부를 사용하려면 아래 exit를 활성화할 수 있습니다.
# (( failed_steps == 0 )) || exit 1
