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

CMD="${1:-}"

case "$CMD" in
  users)
    k6 run create-test-users.js
    ;;

  enter)
    k6 run drop-enter-concurrency.js
    ;;

  wait-active)
    k6 run drop-wait-active.js
    ;;

  confirm)
    k6 run drop-confirm-entry-concurrency.js
    ;;

  lock)
    k6 run drop-lock-concurrency.js
    ;;

  *)
    echo "사용법: ./run-k6.sh {users|enter|wait-active|confirm|lock}"
    echo
    echo "권장 순서:"
    echo "  ./run-k6.sh users"
    echo "  ./run-k6.sh enter"
    echo "  ./run-k6.sh wait-active"
    echo "  ./run-k6.sh confirm"
    echo "  ./run-k6.sh lock"
    exit 1
    ;;
esac
