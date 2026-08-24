#!/usr/bin/env bash
# 사용자 1000명 / 재고 700개 부하 테스트.
#
# 절차는 _common.sh 의 run_tier 에 모여 있다.
#   계정 생성 -> 드롭 생성 -> 롤아웃 -> 예열 -> confirm-entry -> lock -> 판정/진단
#
# 사용법:
#   ./run-1000.sh
#
# 자주 쓰는 조정값(환경변수):
#   PERF_WARMUP_SECONDS=0   예열 대기 생략 (측정값은 나빠진다)
#   REUSE_USERS=false       계정 파일을 무시하고 다시 만든다
#   SAMPLE_INTERVAL=0.5     지표 샘플링 간격
set -Eeuo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_common.sh"

run_tier 1000 700
