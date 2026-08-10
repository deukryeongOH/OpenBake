#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${SCRIPT_DIR}/.env.monitoring"
TEMPLATE_FILE="${SCRIPT_DIR}/prometheus/prometheus.yml.template"
OUTPUT_FILE="${SCRIPT_DIR}/prometheus/prometheus.yml"
COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.monitoring.yml"

if [[ ! -f "${ENV_FILE}" ]]; then
    echo "환경변수 파일이 없습니다: ${ENV_FILE}"
    exit 1
fi

if [[ ! -f "${TEMPLATE_FILE}" ]]; then
    echo "Prometheus 템플릿이 없습니다: ${TEMPLATE_FILE}"
    exit 1
fi

set -a
source "${ENV_FILE}"
set +a

global:
  scrape_interval: 5s
  evaluation_interval: 5s

scrape_configs:
  - job_name: "openbake-core"
    metrics_path: "/actuator/prometheus"
    static_configs:
      - targets:
          - "${OPENBAKE_CORE_TARGET}"

  - job_name: "openbake-member"
    metrics_path: "/actuator/prometheus"
    static_configs:
      - targets:
          - "${OPENBAKE_MEMBER_TARGET}"

  - job_name: "openbake-payment"
    metrics_path: "/actuator/prometheus"
    static_configs:
      - targets:
          - "${OPENBAKE_PAYMENT_TARGET}"

echo "Prometheus Target: ${OPENBAKE_TARGET}"

envsubst \
'${OPENBAKE_CORE_TARGET} ${OPENBAKE_MEMBER_TARGET} ${OPENBAKE_PAYMENT_TARGET}' \
< "${TEMPLATE_FILE}" \
> "${OUTPUT_FILE}"

echo "생성된 Prometheus 설정:"
cat "${OUTPUT_FILE}"

docker compose \
    -f "${COMPOSE_FILE}" \
    up -d --force-recreate prometheus