#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# 지속 부하 실행기 — 외부 호스트(노트북)에서 실행하는 것을 전제로 한다.
#
# 서버 안에서 k6 를 돌리면 2 vCPU 노드에서 부하 생성기가 측정 대상과 CPU 를 다툰다.
# 실제로 100 VU 버스트에서 http_req_blocked p95 가 전체 p95 를 넘겼다 —
# TLS 핸드셰이크와 커넥션 수립이 CPU 경쟁에 밀렸다는 뜻이다.
# 그래서 이 스크립트는 kubectl 을 요구하지 않는다. k6 와 users.json 만 있으면 된다.
#
# 서버 지표는 서버 쪽에서 sample-backend.py 를 따로 띄워 수집한다(README 참고).
#
# 사용법:
#   CORE_BASE_URL=https://3.38.24.67.sslip.io DROP_ID=32 ./run-sustained.sh
#   TARGET=info DROP_ID=32 ./run-sustained.sh          # 계정 없이 인프라만
#
# 조정값:
#   START_RATE / PEAK_RATE / STEP_COUNT / STEP_HOLD    부하 계획
#   MAX_VUS                                            dropped_iterations 나면 올린다
# ---------------------------------------------------------------------------
set -Eeuo pipefail

LOADTEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PERF_DIR="$(cd "$LOADTEST_DIR/.." && pwd)"

die() { printf '\033[31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

command -v k6 >/dev/null 2>&1 || die "k6 가 필요합니다. https://k6.io/docs/get-started/installation/"

CORE_BASE_URL="${CORE_BASE_URL:-}"
[[ -n "$CORE_BASE_URL" ]] || die "CORE_BASE_URL 이 필요합니다. 예: https://3.38.24.67.sslip.io"
CORE_BASE_URL="${CORE_BASE_URL%/}"

DROP_ID="${DROP_ID:-}"
[[ -n "$DROP_ID" ]] || die "DROP_ID 가 필요합니다. 서버에서 prepare-drop.sh 로 만든 드롭 id."

TARGET="${TARGET:-confirm}"
AUTH_MODE="${AUTH_MODE:-gateway}"

if [[ "$TARGET" == "confirm" && ! -f "$PERF_DIR/users.json" ]]; then
  die "TARGET=confirm 은 $PERF_DIR/users.json 이 필요합니다.
     서버에서 만든 파일을 가져오거나(scp) create-users.py 를 여기서 실행하세요.
     계정 없이 인프라만 재려면 TARGET=info 로 실행하세요."
fi

START_RATE="${START_RATE:-20}"
PEAK_RATE="${PEAK_RATE:-100}"
STEP_COUNT="${STEP_COUNT:-5}"
STEP_HOLD="${STEP_HOLD:-90s}"
STEP_RAMP="${STEP_RAMP:-15s}"
WARMUP_HOLD="${WARMUP_HOLD:-30s}"
PRE_ALLOCATED_VUS="${PRE_ALLOCATED_VUS:-100}"
MAX_VUS="${MAX_VUS:-800}"
SLOW_THRESHOLD_MS="${SLOW_THRESHOLD_MS:-1500}"
HTTP_TIMEOUT="${HTTP_TIMEOUT:-30s}"

RESULTS_ROOT="${RESULTS_ROOT:-$PERF_DIR/results/sustained}"
TS="$(date -u +%Y%m%dT%H%M%SZ)"
RUN_DIR="$RESULTS_ROOT/${TS}-sustained-${TARGET}-drop${DROP_ID}"
mkdir -p "$RUN_DIR"

cat <<EOF_HEAD

========================================================
 지속 부하 테스트
========================================================
 대상        : $CORE_BASE_URL  (drop $DROP_ID, TARGET=$TARGET)
 부하 계획   : ${START_RATE} -> ${PEAK_RATE} req/s, ${STEP_COUNT}단계
 단계 유지   : ${STEP_HOLD}   (HPA 평가 15s + Pod 기동을 감안한 값)
 VU 상한     : ${MAX_VUS}
 결과        : $RUN_DIR
========================================================

 서버 지표를 같이 받으려면 지금 서버에서 아래를 먼저 띄워 두세요:
   python3 performance-test/loadtest/sample-backend.py --out sustained-sample.csv

EOF_HEAD

status=0
set +e
# p(99) 는 k6 기본 summaryTrendStats 에 없어 명시하지 않으면 summary-export 에서 빠진다.
# count 도 같이 넣는다 — 이 값을 지정하는 순간 나열한 통계만 남아 Trend 의 count 가 사라진다.
K6_SUMMARY_TREND_STATS='avg,min,med,p(90),p(95),p(99),max,count' \
k6 run \
  --summary-export "$RUN_DIR/summary.json" \
  --tag "testid=${TS}-sustained-${TARGET}" \
  -e "CORE_BASE_URL=$CORE_BASE_URL" \
  -e "DROP_ID=$DROP_ID" \
  -e "TARGET=$TARGET" \
  -e "AUTH_MODE=$AUTH_MODE" \
  -e "START_RATE=$START_RATE" \
  -e "PEAK_RATE=$PEAK_RATE" \
  -e "STEP_COUNT=$STEP_COUNT" \
  -e "STEP_HOLD=$STEP_HOLD" \
  -e "STEP_RAMP=$STEP_RAMP" \
  -e "WARMUP_HOLD=$WARMUP_HOLD" \
  -e "PRE_ALLOCATED_VUS=$PRE_ALLOCATED_VUS" \
  -e "MAX_VUS=$MAX_VUS" \
  -e "SLOW_THRESHOLD_MS=$SLOW_THRESHOLD_MS" \
  -e "HTTP_TIMEOUT=$HTTP_TIMEOUT" \
  "$LOADTEST_DIR/drop-sustained-load.js" 2>&1 | tee "$RUN_DIR/console.txt"
status=${PIPESTATUS[0]}
set -e

cat > "$RUN_DIR/metadata.env" <<EOF_META
scenario=sustained
target=$TARGET
drop_id=$DROP_ID
core_base_url=$CORE_BASE_URL
start_rate=$START_RATE
peak_rate=$PEAK_RATE
step_count=$STEP_COUNT
step_hold=$STEP_HOLD
max_vus=$MAX_VUS
k6_exit=$status
load_generator=$(hostname)
EOF_META

echo
echo "결과: $RUN_DIR"
echo
echo "서버 지표 CSV 를 받아온 뒤 판정하려면:"
echo "  python3 $LOADTEST_DIR/diagnose.py \\"
echo "    --summary $RUN_DIR/summary.json \\"
echo "    --sample <서버에서 받아온 sample.csv> \\"
echo "    --scenario confirm --users $PEAK_RATE \\"
echo "    --load-generator-location '$(hostname) (외부 호스트)'"