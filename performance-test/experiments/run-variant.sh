#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PERF_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
CAPACITY_RUNNER="$PERF_DIR/capacity/run-lock-capacity-scan.sh"

VARIANT="${1:-}"
PLAN_FILE="${2:-}"

if [[ -z "$VARIANT" || -z "$PLAN_FILE" ]]; then
    echo "사용법:"
    echo "  ./experiments/run-variant.sh baseline experiments/baseline-plan.csv"
    echo "  ./experiments/run-variant.sh candidate experiments/candidate-plan.csv"
    exit 1
fi

if [[ ! "$VARIANT" =~ ^[A-Za-z0-9_.-]+$ ]]; then
    echo "ERROR: variant는 영문/숫자/._- 만 사용할 수 있습니다: $VARIANT"
    exit 1
fi

if [[ ! -f "$PLAN_FILE" ]]; then
    echo "ERROR: plan file 없음: $PLAN_FILE"
    exit 1
fi

if [[ ! -x "$CAPACITY_RUNNER" ]]; then
    chmod +x "$CAPACITY_RUNNER"
fi

GIT_COMMIT="unavailable"
GIT_DIRTY="unknown"
if git -C "$PERF_DIR/.." rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    GIT_COMMIT="$(git -C "$PERF_DIR/.." rev-parse HEAD 2>/dev/null || true)"
    if [[ -n "$(git -C "$PERF_DIR/.." status --porcelain 2>/dev/null || true)" ]]; then
        GIT_DIRTY="true"
    else
        GIT_DIRTY="false"
    fi
fi

STAMP="$(date '+%Y%m%d-%H%M%S')"
HISTORY_DIR="$SCRIPT_DIR/history"
mkdir -p "$HISTORY_DIR"
HISTORY_FILE="$HISTORY_DIR/${STAMP}-${VARIANT}.env"

{
    echo "variant=$VARIANT"
    echo "started_at=$(date -Iseconds)"
    echo "plan_file=$(realpath "$PLAN_FILE")"
    echo "git_commit=$GIT_COMMIT"
    echo "git_dirty=$GIT_DIRTY"
    echo "note=${EXPERIMENT_NOTE:-}"
} > "$HISTORY_FILE"

echo "========================================"
echo " OpenBake Performance Experiment"
echo "========================================"
echo "Variant : $VARIANT"
echo "Plan    : $PLAN_FILE"
echo "Commit  : $GIT_COMMIT"
echo "Dirty   : $GIT_DIRTY"
echo "History : $HISTORY_FILE"
echo

if [[ "$GIT_DIRTY" == "true" ]]; then
    echo "WARN: working tree에 미커밋 변경이 있습니다. baseline/candidate 재현성을 위해 변경 내용을 기록하세요."
    echo
fi

set +e
EXPERIMENT_VARIANT="$VARIANT" \
EXPERIMENT_NOTE="${EXPERIMENT_NOTE:-}" \
"$CAPACITY_RUNNER" "$PLAN_FILE"
status=$?
set -e

{
    echo "finished_at=$(date -Iseconds)"
    echo "exit_code=$status"
} >> "$HISTORY_FILE"

exit "$status"
