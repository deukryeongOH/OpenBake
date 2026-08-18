#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PERF_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ROOT_DIR="$(cd "$PERF_DIR/.." && pwd)"
PLAN_FILE="${1:-$SCRIPT_DIR/capacity-plan.csv}"
RESULTS_ROOT="${RESULTS_ROOT:-$PERF_DIR/results/runs}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
PROMETHEUS_JOB="${PROMETHEUS_JOB:-openbake-core}"
CAPACITY_VERIFY_MONITORING="${CAPACITY_VERIFY_MONITORING:-true}"
COLLECT_OBSERVABILITY="${COLLECT_OBSERVABILITY:-true}"
OBSERVABILITY_SETTLE_SECONDS="${OBSERVABILITY_SETTLE_SECONDS:-2}"
PROMETHEUS_PRIME_SECONDS="${PROMETHEUS_PRIME_SECONDS:-6}"
PROMETHEUS_RATE_WINDOW="${PROMETHEUS_RATE_WINDOW:-5s}"

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

# 계획 자체의 상태 오염 가능성을 먼저 차단합니다.
python3 - "$PLAN_FILE" <<'PY'
import csv, sys
from pathlib import Path

path = Path(sys.argv[1])
rows = list(csv.DictReader(path.open(encoding='utf-8-sig')))
if not rows:
    raise SystemExit('ERROR: capacity plan이 비어 있습니다.')
required = {'users', 'drop_id', 'expected_success', 'expected_sold_out'}
missing = required - set(rows[0])
if missing:
    raise SystemExit(f"ERROR: capacity plan 컬럼 누락: {sorted(missing)}")
seen = set()
for line_no, row in enumerate(rows, start=2):
    try:
        users = int(row['users'])
        success = int(row['expected_success'])
        soldout = int(row['expected_sold_out'])
    except ValueError as exc:
        raise SystemExit(f'ERROR: line {line_no} 숫자 형식 오류: {exc}')
    drop_id = row['drop_id'].strip()
    if not drop_id or drop_id == 'CHANGE_ME':
        raise SystemExit(f'ERROR: line {line_no} drop_id를 설정하세요.')
    if drop_id in seen:
        raise SystemExit(f'ERROR: drop_id={drop_id}가 중복입니다. 상태 변경 테스트는 독립 Drop을 사용하세요.')
    seen.add(drop_id)
    if users <= 0:
        raise SystemExit(f'ERROR: line {line_no} users는 1 이상이어야 합니다.')
    if success < 0 or soldout < 0 or success + soldout != users:
        raise SystemExit(
            f'ERROR: line {line_no} expected_success + expected_sold_out가 users와 같아야 합니다. '
            f'users={users}, success={success}, soldout={soldout}'
        )
print(f'OK: capacity plan 검증 완료 (steps={len(rows)}, unique_drops={len(seen)})')
PY

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

if [[ "$CAPACITY_VERIFY_MONITORING" == "true" ]]; then
    VERIFY_SCRIPT="$ROOT_DIR/monitoring/verify-monitoring.sh"
    if [[ ! -x "$VERIFY_SCRIPT" && -f "$VERIFY_SCRIPT" ]]; then
        chmod +x "$VERIFY_SCRIPT"
    fi
    if [[ -x "$VERIFY_SCRIPT" ]]; then
        echo
        echo "==> Monitoring preflight"
        PROMETHEUS_URL="$PROMETHEUS_URL" "$VERIFY_SCRIPT"
    else
        echo "WARN: monitoring/verify-monitoring.sh를 찾지 못해 preflight를 건너뜁니다."
    fi
fi

echo
echo "========================================"
echo " OpenBake Stock Reservation Capacity Scan"
echo "========================================"
echo "Plan       : $PLAN_FILE"
echo "Users      : $actual_users"
echo "Results    : $RESULTS_ROOT"
echo "Prometheus : $PROMETHEUS_URL"
echo "Job        : $PROMETHEUS_JOB"
echo "Rate window: $PROMETHEUS_RATE_WINDOW"
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

if [[ "$COLLECT_OBSERVABILITY" == "true" && "$PROMETHEUS_PRIME_SECONDS" -gt 0 ]]; then
    echo
    echo "==> Prometheus short-window query 준비 (${PROMETHEUS_PRIME_SECONDS}s)"
    sleep "$PROMETHEUS_PRIME_SECONDS"
fi

mkdir -p "$RESULTS_ROOT"
failed_steps=0
collected_steps=0

while IFS=, read -r users drop_id expected_success expected_sold_out; do
    [[ "$users" == "users" ]] && continue
    [[ -z "$users" ]] && continue
    # CRLF 방어
    expected_sold_out="${expected_sold_out%$'\r'}"

    echo
    echo "----------------------------------------"
    echo " Capacity step: users=${users}, drop=${drop_id}"
    echo "----------------------------------------"

    set +e
    (
        cd "$PERF_DIR"
        RESULTS_ROOT="$RESULTS_ROOT" \
        USER_COUNT="$users" \
        DROP_ID="$drop_id" \
        EXPECTED_SUCCESS="$expected_success" \
        EXPECTED_SOLD_OUT="$expected_sold_out" \
        ./run-k6.sh lock
    )
    step_status=$?
    set -e

    latest_run="$(find "$RESULTS_ROOT" -maxdepth 1 -mindepth 1 -type d \
        -name "*lock-concurrency-u${users}-drop${drop_id}*" -printf '%T@ %p\n' 2>/dev/null \
        | sort -nr | head -n 1 | cut -d' ' -f2- || true)"

    if [[ "$COLLECT_OBSERVABILITY" == "true" && -n "$latest_run" ]]; then
        echo
        echo "==> Prometheus 관측 지표 수집 대기 (${OBSERVABILITY_SETTLE_SECONDS}s)"
        sleep "$OBSERVABILITY_SETTLE_SECONDS"
        set +e
        python3 "$SCRIPT_DIR/collect-observability.py" \
            --run-dir "$latest_run" \
            --prometheus-url "$PROMETHEUS_URL" \
            --job "$PROMETHEUS_JOB" \
            --rate-window "$PROMETHEUS_RATE_WINDOW" \
            --overwrite
        obs_status=$?
        set -e
        if (( obs_status == 0 )); then
            collected_steps=$((collected_steps + 1))
        else
            echo "WARN: users=${users} observability 수집 실패. k6 결과는 보존됩니다."
        fi
    fi

    if (( step_status != 0 )); then
        echo "WARN: users=${users} step이 NFR/정합성 기준을 통과하지 못했습니다. 다음 step을 계속합니다."
        failed_steps=$((failed_steps + 1))
    fi

done < "$PLAN_FILE"

echo
echo "==> Capacity 결과 집계"
python3 "$SCRIPT_DIR/analyze-capacity.py"


echo
echo "========================================"
echo " Capacity Scan Finished"
echo "========================================"
echo "failed steps          = ${failed_steps}"
echo "observability collected = ${collected_steps}"
echo
echo "결과:"
echo "  $SCRIPT_DIR/capacity-summary.md"
echo "  $SCRIPT_DIR/capacity-repeat-summary.md"
echo
echo "해석 시 bottleneck_candidates는 원인 확정이 아니라 후보 신호로 사용하세요."

# Capacity 한계 탐색에서는 실패 step도 필요한 데이터이므로 모든 step을 실행한 뒤 종료합니다.
# CI에서 threshold 실패를 pipeline 실패로 취급하려면 아래 exit를 활성화할 수 있습니다.
# (( failed_steps == 0 )) || exit 1
