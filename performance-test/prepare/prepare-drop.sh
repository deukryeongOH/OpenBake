#!/usr/bin/env bash
set -Eeuo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

PROFILE="${1:-}"
COUNT="${2:-}"

if [[ "$PROFILE" != "local" && "$PROFILE" != "server" ]] || [[ -z "$COUNT" ]]; then
  echo "사용법: ./prepare-drop.sh <local|server> <USER_COUNT>"
  echo "예: ./prepare-drop.sh local 100"
  echo "예: ./prepare-drop.sh server 500"
  exit 1
fi

cd "$DIR"
python3 perf-data.py "$PROFILE" drop "$COUNT"
