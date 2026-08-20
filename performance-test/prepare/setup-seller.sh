#!/usr/bin/env bash
set -Eeuo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROFILE="${1:-local}"

if [[ "$PROFILE" != "local" && "$PROFILE" != "server" ]]; then
  echo "사용법: ./setup-seller.sh [local|server]"
  exit 1
fi

cd "$DIR"
python3 perf-data.py "$PROFILE" seller
