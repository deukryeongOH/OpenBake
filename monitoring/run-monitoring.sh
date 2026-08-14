#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ENV_FILE="$SCRIPT_DIR/.env.monitoring"
TEMPLATE_FILE="$SCRIPT_DIR/prometheus/prometheus.yml.template"
OUTPUT_FILE="$SCRIPT_DIR/prometheus/prometheus.yml"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.monitoring.yml"

echo "========================================"
echo " OpenBake Monitoring Runner"
echo "========================================"

if [[ ! -f "$ENV_FILE" ]]; then
    echo "ERROR: .env.monitoring 파일이 없습니다."
    echo
    echo "다음 명령으로 생성하세요:"
    echo "cp .env.monitoring.example .env.monitoring"
    exit 1
fi

set -a
source "$ENV_FILE"
set +a

for var in \
    OPENBAKE_CORE_TARGET \
    OPENBAKE_MEMBER_TARGET \
    OPENBAKE_PAYMENT_TARGET \
    GRAFANA_ADMIN_USER \
    GRAFANA_ADMIN_PASSWORD
do
    if [[ -z "${!var:-}" ]]; then
        echo "ERROR: ${var} 환경변수가 설정되어 있지 않습니다."
        exit 1
    fi
done

if [[ "$GRAFANA_ADMIN_PASSWORD" == "CHANGE_ME_STRONG_PASSWORD" ]]; then
    echo "ERROR: GRAFANA_ADMIN_PASSWORD 기본값을 실제 비밀번호로 변경하세요."
    exit 1
fi

if ! command -v envsubst >/dev/null 2>&1; then
    echo
    echo "ERROR: envsubst 명령이 없습니다."
    echo "설치: sudo apt install -y gettext-base"
    exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
    echo "ERROR: docker 명령을 찾을 수 없습니다."
    exit 1
fi

echo
echo "모니터링 대상:"
echo "  Core    : $OPENBAKE_CORE_TARGET"
echo "  Member  : $OPENBAKE_MEMBER_TARGET"
echo "  Payment : $OPENBAKE_PAYMENT_TARGET"

echo
echo "==> Prometheus 설정 생성"
envsubst \
'${OPENBAKE_CORE_TARGET} ${OPENBAKE_MEMBER_TARGET} ${OPENBAKE_PAYMENT_TARGET}' \
< "$TEMPLATE_FILE" \
> "$OUTPUT_FILE"

echo "==> 생성 완료: $OUTPUT_FILE"

echo
echo "==> Prometheus + Grafana 실행"
docker compose \
    --env-file "$ENV_FILE" \
    -f "$COMPOSE_FILE" \
    up -d --force-recreate

echo
echo "==> 컨테이너 상태"
docker compose \
    --env-file "$ENV_FILE" \
    -f "$COMPOSE_FILE" \
    ps

echo
echo "========================================"
echo " Monitoring Started"
echo "========================================"
echo "Prometheus : http://localhost:9090"
echo "Grafana    : http://localhost:3001"
echo
echo "상태 확인:"
echo "  ./verify-monitoring.sh"
