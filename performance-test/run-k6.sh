#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ENV_FILE="$SCRIPT_DIR/.env.k6"
COMMAND="${1:-}"
RESULTS_ROOT="${RESULTS_ROOT:-$SCRIPT_DIR/results/runs}"
K6_SUMMARY_TREND_STATS="${K6_SUMMARY_TREND_STATS:-avg,min,med,p(90),p(95),p(99),max}"

if [[ ! -f "$ENV_FILE" ]]; then
    echo "ERROR: .env.k6 파일이 없습니다."
    echo
    echo "다음 명령으로 생성하세요."
    echo "cp .env.k6.example .env.k6"
    exit 1
fi

# CLI에서 USER_COUNT=300 ./run-k6.sh lock 처럼 전달한 값은
# .env.k6보다 우선하도록 보존합니다. Capacity scan이 이 동작을 사용합니다.
OVERRIDE_VARS=(
    CORE_BASE_URL MEMBER_BASE_URL DROP_ID USER_COUNT START_INDEX QUANTITY
    EXPECTED_SUCCESS EXPECTED_SOLD_OUT LOGIN_PATH TEST_PASSWORD TOKEN_PATH
    EMAIL_FIELD PASSWORD_FIELD EMAIL_PREFIX EMAIL_DOMAIN OUTPUT_FILE REQUEST_TIMEOUT
    K6_PROMETHEUS_RW_ENABLED K6_PROMETHEUS_RW_SERVER_URL
    K6_PROMETHEUS_RW_TREND_STATS K6_PROMETHEUS_RW_PUSH_INTERVAL
)
declare -A CALLER_OVERRIDES=()
for var in "${OVERRIDE_VARS[@]}"; do
    if [[ -n "${!var+x}" ]]; then
        CALLER_OVERRIDES["$var"]="${!var}"
    fi
done

set -a
source "$ENV_FILE"
set +a

for var in "${!CALLER_OVERRIDES[@]}"; do
    printf -v "$var" '%s' "${CALLER_OVERRIDES[$var]}"
    export "$var"
done

require_var() {
    local name="$1"
    if [[ -z "${!name:-}" ]]; then
        echo "ERROR: ${name} 환경변수가 필요합니다."
        exit 1
    fi
}

require_command() {
    local name="$1"
    if ! command -v "$name" >/dev/null 2>&1; then
        echo "ERROR: ${name} 명령을 찾을 수 없습니다."
        exit 1
    fi
}

require_users_file() {
    local users_file="${OUTPUT_FILE:-users.json}"

    if [[ "$users_file" != "users.json" ]]; then
        echo "ERROR: 현재 k6-users.js는 users.json을 사용합니다. OUTPUT_FILE=users.json으로 설정하세요."
        exit 1
    fi

    if [[ ! -f "$users_file" ]]; then
        echo "ERROR: ${users_file}이 없습니다."
        echo "먼저 실행하세요: ./run-k6.sh users"
        exit 1
    fi
}

print_config() {
    echo
    echo "========================================"
    echo " OpenBake k6"
    echo "========================================"
    [[ -n "${CORE_BASE_URL:-}" ]] && echo "Core      : ${CORE_BASE_URL}"
    [[ -n "${MEMBER_BASE_URL:-}" ]] && echo "Member    : ${MEMBER_BASE_URL}"
    [[ -n "${DROP_ID:-}" ]] && echo "Drop ID   : ${DROP_ID}"
    [[ -n "${USER_COUNT:-}" ]] && echo "Users     : ${USER_COUNT}"
    echo "========================================"
    echo
}

write_metadata() {
    local file="$1"
    local test_name="$2"
    local started_at="$3"

    {
        echo "test_name=${test_name}"
        echo "started_at=${started_at}"
        echo "core_base_url=${CORE_BASE_URL:-}"
        echo "member_base_url=${MEMBER_BASE_URL:-}"
        echo "drop_id=${DROP_ID:-}"
        echo "user_count=${USER_COUNT:-}"
        echo "quantity=${QUANTITY:-}"
        echo "expected_success=${EXPECTED_SUCCESS:-}"
        echo "expected_sold_out=${EXPECTED_SOLD_OUT:-}"
        echo "prometheus_remote_write=${K6_PROMETHEUS_RW_ENABLED:-false}"
        echo "prometheus_remote_write_url=${K6_PROMETHEUS_RW_SERVER_URL:-}"
        echo "k6_version=$(k6 version 2>/dev/null | head -n 1 || true)"

        if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
            echo "git_commit=$(git rev-parse HEAD 2>/dev/null || true)"
        else
            echo "git_commit=unavailable"
        fi
    } > "$file"
}

run_test() {
    local test_name="$1"
    local script_file="$2"
    local started_at
    local run_id
    local run_dir
    local status

    require_command k6

    started_at="$(date -Iseconds)"
    run_id="$(date '+%Y%m%d-%H%M%S')-${test_name}-u${USER_COUNT:-na}-drop${DROP_ID:-na}"
    run_dir="${RESULTS_ROOT}/${run_id}"

    mkdir -p "$run_dir"
    write_metadata "$run_dir/metadata.env" "$test_name" "$started_at"

    echo "==> 결과 저장 경로: $run_dir"
    echo

    local -a output_args=()
    local -a tag_args=(--tag "testid=${run_id}")

    if [[ "${K6_PROMETHEUS_RW_ENABLED:-false}" == "true" ]]; then
        require_var K6_PROMETHEUS_RW_SERVER_URL
        export K6_PROMETHEUS_RW_TREND_STATS="${K6_PROMETHEUS_RW_TREND_STATS:-p(95),p(99),min,max}"
        export K6_PROMETHEUS_RW_PUSH_INTERVAL="${K6_PROMETHEUS_RW_PUSH_INTERVAL:-1s}"
        output_args=(-o experimental-prometheus-rw)

        echo "==> Prometheus Remote Write: ${K6_PROMETHEUS_RW_SERVER_URL}"
        echo "==> testid: ${run_id}"
        echo
    fi

    set +e
    k6 run \
        --summary-export "$run_dir/summary.json" \
        --summary-trend-stats "$K6_SUMMARY_TREND_STATS" \
        "${tag_args[@]}" \
        "${output_args[@]}" \
        "$script_file" \
        2>&1 | tee "$run_dir/console.txt"
    status=${PIPESTATUS[0]}
    set -e

    {
        echo "finished_at=$(date -Iseconds)"
        echo "exit_code=${status}"
        if [[ "$status" -eq 0 ]]; then
            echo "result=PASS"
        else
            echo "result=FAIL"
        fi
    } >> "$run_dir/metadata.env"

    echo
    if [[ "$status" -eq 0 ]]; then
        echo "PASS: ${test_name}"
    else
        echo "FAIL: ${test_name} (k6 exit code=${status})"
    fi
    echo "결과: $run_dir"

    return "$status"
}

usage() {
    echo "사용법:"
    echo "  ./run-k6.sh users"
    echo "  ./run-k6.sh enter"
    echo "  ./run-k6.sh confirm"
    echo "  ./run-k6.sh lock"
}

case "$COMMAND" in
    users)
        require_command python3
        require_var MEMBER_BASE_URL
        require_var USER_COUNT
        require_var LOGIN_PATH
        require_var TEST_PASSWORD

        print_config
        echo "==> 테스트 사용자 로그인 및 users.json 생성"
        python3 generate-user-json.py
        ;;

    enter)
        require_var CORE_BASE_URL
        require_var DROP_ID
        require_var USER_COUNT
        require_users_file

        print_config
        echo "==> Drop 대기열 진입 동시성 테스트"
        run_test "enter-concurrency" "drop-enter-concurrency.js"
        ;;

    confirm)
        require_var CORE_BASE_URL
        require_var DROP_ID
        require_var USER_COUNT
        require_users_file

        print_config
        echo "==> Drop 입장 확정 동시성 테스트"
        run_test "confirm-concurrency" "drop-confirm-entry-concurrency.js"
        ;;

    lock)
        require_var CORE_BASE_URL
        require_var DROP_ID
        require_var USER_COUNT
        require_var QUANTITY
        require_var EXPECTED_SUCCESS
        require_var EXPECTED_SOLD_OUT
        require_users_file

        print_config
        echo "==> Drop 재고 선점 동시성 테스트"
        run_test "lock-concurrency" "drop-lock-concurrency.js"
        ;;

    *)
        usage
        exit 1
        ;;
esac
