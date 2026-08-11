#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(
    cd "$(dirname "${BASH_SOURCE[0]}")"
    pwd
)"

ENV_FILE="${SCRIPT_DIR}/.env.k6"
RESULT_DIR="${SCRIPT_DIR}/results"

if [[ ! -f "${ENV_FILE}" ]]; then
    echo ".env.k6 파일이 없습니다."
    echo "먼저 다음 명령을 실행하세요:"
    echo "  cp .env.k6.example .env.k6"
    exit 1
fi

set -a
source "${ENV_FILE}"
set +a

echo "$BASE_URL"
echo "$DROP_ID"
echo "$USER_COUNT"
echo "$QUANTITY"

mkdir -p "${RESULT_DIR}"

COMMAND="${1:-}"

case "${COMMAND}" in
    users)
        cd "${SCRIPT_DIR}"
        python3 generate-user-json.py
        ;;

    enter)
        cd "${SCRIPT_DIR}"
        k6 run drop-enter-concurrency.js \
            2>&1 | tee \
            "${RESULT_DIR}/drop-enter-${USER_COUNT}.txt"
        ;;

    confirm)
        cd "${SCRIPT_DIR}"
        k6 run drop-confirm-entry-concurrency.js \
            2>&1 | tee \
            "${RESULT_DIR}/drop-confirm-entry-${USER_COUNT}.txt"
        ;;

    lock)
        cd "${SCRIPT_DIR}"
        k6 run drop-lock-concurrency.js \
            2>&1 | tee \
            "${RESULT_DIR}/drop-lock-${USER_COUNT}.txt"
        ;;

    *)
        echo "사용법:"
        echo "  ./run-k6.sh users"
        echo "  ./run-k6.sh enter"
        echo "  ./run-k6.sh confirm"
        echo "  ./run-k6.sh lock"
        exit 1
        ;;
esac
