#!/usr/bin/env bash
set -Eeuo pipefail

PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
GRAFANA_URL="${GRAFANA_URL:-http://localhost:3001}"

echo "========================================"
echo " OpenBake Monitoring Verification"
echo "========================================"


printf '\n[1/3] Prometheus health\n'
curl -fsS "${PROMETHEUS_URL}/-/ready" >/dev/null
echo "OK: ${PROMETHEUS_URL}"

printf '\n[2/3] Grafana health\n'
curl -fsS "${GRAFANA_URL}/api/health" >/dev/null
echo "OK: ${GRAFANA_URL}"

printf '\n[3/3] Prometheus scrape targets\n'
curl -fsS "${PROMETHEUS_URL}/api/v1/targets" | python3 -c '
import json, sys
payload = json.load(sys.stdin)
targets = payload.get("data", {}).get("activeTargets", [])
if not targets:
    print("WARN: active target가 없습니다.")
    raise SystemExit(0)
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

printf '\nGrafana dashboard:\n'
echo "  ${GRAFANA_URL}/dashboards"
echo "  Folder: OpenBake"
echo "  Dashboard: OpenBake Performance Overview"
