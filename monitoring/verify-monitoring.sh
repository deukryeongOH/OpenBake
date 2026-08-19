#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env.monitoring"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
GRAFANA_URL="${GRAFANA_URL:-http://localhost:3001}"

if [[ -f "$ENV_FILE" ]]; then
    set -a
    source "$ENV_FILE"
    set +a
fi

# 세 서비스 모두 scrape하는 구성이므로 기본값도 세 서비스를 모두 검증합니다.
REQUIRED_PROMETHEUS_JOBS="${REQUIRED_PROMETHEUS_JOBS:-openbake-core,openbake-member,openbake-payment}"

echo "========================================"
echo " OpenBake Monitoring Verification"
echo "========================================"

printf '\n[1/5] Prometheus health\n'
curl -fsS "${PROMETHEUS_URL}/-/ready" >/dev/null
echo "OK: ${PROMETHEUS_URL}"

printf '\n[2/5] Grafana health\n'
curl -fsS "${GRAFANA_URL}/api/health" >/dev/null
echo "OK: ${GRAFANA_URL}"

printf '\n[3/5] Prometheus scrape targets\n'
TARGET_JSON="$(curl -fsS "${PROMETHEUS_URL}/api/v1/targets")"
printf '%s' "$TARGET_JSON" | python3 -c '
import json, sys
payload = json.load(sys.stdin)
targets = payload.get("data", {}).get("activeTargets", [])
if not targets:
    print("WARN: active target가 없습니다.")
for target in targets:
    labels = target.get("labels", {})
    job = labels.get("job", "unknown")
    health = target.get("health", "unknown")
    url = target.get("scrapeUrl", "")
    err = target.get("lastError", "")
    print(f"- {job:20} {health:7} {url}")
    if err:
        print(f"  error: {err}")
'

printf '\n[4/5] Required target verification\n'
TARGET_JSON_ENV="$TARGET_JSON" REQUIRED_JOBS_ENV="$REQUIRED_PROMETHEUS_JOBS" python3 - <<'PY'
import json, os
required = [x.strip() for x in os.environ.get('REQUIRED_JOBS_ENV', '').split(',') if x.strip()]
payload = json.loads(os.environ['TARGET_JSON_ENV'])
targets = payload.get('data', {}).get('activeTargets', [])
health = {
    t.get('labels', {}).get('job', ''): t.get('health', 'unknown')
    for t in targets
}
failed = []
for job in required:
    state = health.get(job, 'missing')
    if state == 'up':
        print(f'OK: {job} = UP')
    else:
        print(f'FAIL: {job} = {state.upper()}')
        failed.append(job)
if failed:
    raise SystemExit(1)
PY

printf '\n[5/5] Core metric availability\n'
python3 "$SCRIPT_DIR/verify-metrics.py" --prometheus-url "$PROMETHEUS_URL" --job openbake-core

printf '\nGrafana dashboard:\n'
echo "  ${GRAFANA_URL}/dashboards"
echo "  Folder: OpenBake"
echo "  Dashboard: OpenBake Performance Overview"
