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

if ! [[ "$COUNT" =~ ^[1-9][0-9]*$ ]]; then
  echo "ERROR: USER_COUNT는 1 이상의 정수여야 합니다. 입력값=$COUNT" >&2
  exit 1
fi

cd "$DIR"

# 성능테스트 Drop 시간대
export PERF_TIMEZONE="${PERF_TIMEZONE:-Asia/Seoul}"

echo "[PERF] 성능테스트용 Drop 생성 시작"
echo "[PERF] profile=$PROFILE, users=$COUNT"
echo "[PERF] timezone=$PERF_TIMEZONE"
echo "[PERF] 생성 직후 Drop을 한국시간 기준 -5분 ~ +2시간 구간으로 ACTIVE 처리합니다."

python3 perf-data.py "$PROFILE" drop "$COUNT"

echo
echo "[PERF] 준비 완료. 다음 명령을 실행하세요:"
echo "  cd .."
echo "  ./run-k6.sh enter"