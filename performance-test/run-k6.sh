#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ENV_FILE="$SCRIPT_DIR/.env.k6"

# --------------------------------------------------
# 기본 파일 확인
# --------------------------------------------------

if [[ ! -f "$ENV_FILE" ]]; then
    echo "ERROR: .env.k6 파일이 없습니다."
    echo
    echo "다음 명령으로 생성하세요."
    echo "cp .env.k6.example .env.k6"
    exit 1
fi

# --------------------------------------------------
# .env.k6 로드
# --------------------------------------------------

set -a
source "$ENV_FILE"
set +a

# --------------------------------------------------
# 필수 환경변수 검사
# --------------------------------------------------

: "${CORE_BASE_URL:?CORE_BASE_URL이 필요합니다.}"
: "${MEMBER_BASE_URL:?MEMBER_BASE_URL이 필요합니다.}"
: "${PAYMENT_BASE_URL:?PAYMENT_BASE_URL이 필요합니다.}"

: "${DROP_ID:?DROP_ID가 필요합니다.}"
: "${USER_COUNT:?USER_COUNT가 필요합니다.}"

COMMAND="${1:-}"

print_config() {
    echo
    echo "========================================"
    echo " OpenBake k6"
    echo "========================================"
    echo "Core      : ${CORE_BASE_URL}"
    echo "Member    : ${MEMBER_BASE_URL}"
    echo "Payment   : ${PAYMENT_BASE_URL}"
    echo "Drop ID   : ${DROP_ID}"
    echo "Users     : ${USER_COUNT}"
    echo "========================================"
    echo
}

usage() {
    echo "사용법:"
    echo
    echo "  ./run-k6.sh users"
    echo "  ./run-k6.sh enter"
    echo "  ./run-k6.sh confirm"
    echo "  ./run-k6.sh lock"
    echo
    echo "예:"
    echo "  ./run-k6.sh users"
    echo "  ./run-k6.sh enter"
}

# --------------------------------------------------
# 실행 명령
# --------------------------------------------------

case "$COMMAND" in

    users)
        print_config

        echo "==> 테스트 사용자 로그인 및 users.json 생성"
        echo "로그인 서버: ${MEMBER_BASE_URL}"
        echo

        # generate-user-json.py는 아직 BASE_URL을 사용하므로
        # Member 서비스 주소를 BASE_URL로 전달
        BASE_URL="$MEMBER_BASE_URL" \
        python3 generate-user-json.py

        echo
        echo "==> users.json 생성 완료"

        if [[ -f "${OUTPUT_FILE:-users.json}" ]]; then
            echo "파일: ${OUTPUT_FILE:-users.json}"
            echo "사용자 수:"
            python3 - <<PY
import json

with open("${OUTPUT_FILE:-users.json}", encoding="utf-8") as f:
    users = json.load(f)

print(len(users))
PY
        fi
        ;;

    enter)
        print_config

        if [[ ! -f "users.json" ]]; then
            echo "ERROR: users.json이 없습니다."
            echo "먼저 실행하세요:"
            echo "./run-k6.sh users"
            exit 1
        fi

        echo "==> Drop 대기열 진입 동시성 테스트"
        echo

        # Drop API는 Core 서비스
        BASE_URL="$CORE_BASE_URL" \
        k6 run drop-enter-concurrency.js
        ;;

    confirm)
        print_config

        if [[ ! -f "users.json" ]]; then
            echo "ERROR: users.json이 없습니다."
            echo "먼저 실행하세요:"
            echo "./run-k6.sh users"
            exit 1
        fi

        echo "==> Drop 입장 확정 동시성 테스트"
        echo

        BASE_URL="$CORE_BASE_URL" \
        k6 run drop-confirm-entry-concurrency.js
        ;;

    lock)
        print_config

        if [[ ! -f "users.json" ]]; then
            echo "ERROR: users.json이 없습니다."
            echo "먼저 실행하세요:"
            echo "./run-k6.sh users"
            exit 1
        fi

        echo "==> Drop 재고 선점 동시성 테스트"
        echo

        BASE_URL="$CORE_BASE_URL" \
        k6 run drop-lock-concurrency.js
        ;;

    *)
        usage
        exit 1
        ;;
esac