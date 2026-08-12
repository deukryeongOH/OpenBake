#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ENV_FILE="$SCRIPT_DIR/.env.k6"
COMMAND="${1:-}"

if [[ ! -f "$ENV_FILE" ]]; then
    echo "ERROR: .env.k6 파일이 없습니다."
    echo
    echo "다음 명령으로 생성하세요."
    echo "cp .env.k6.example .env.k6"
    exit 1
fi

set -a
source "$ENV_FILE"
set +a

require_var() {
    local name="$1"
    if [[ -z "${!name:-}" ]]; then
        echo "ERROR: ${name} 환경변수가 필요합니다."
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

usage() {
    echo "사용법:"
    echo "  ./run-k6.sh users"
    echo "  ./run-k6.sh enter"
    echo "  ./run-k6.sh confirm"
    echo "  ./run-k6.sh lock"
}

case "$COMMAND" in
    users)
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
        k6 run drop-enter-concurrency.js
        ;;

    confirm)
        require_var CORE_BASE_URL
        require_var DROP_ID
        require_var USER_COUNT
        require_users_file

        print_config
        echo "==> Drop 입장 확정 동시성 테스트"
        k6 run drop-confirm-entry-concurrency.js
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
        k6 run drop-lock-concurrency.js
        ;;

    *)
        usage
        exit 1
        ;;
esac
