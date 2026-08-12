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

# .env.monitoring 확인
if [[ ! -f "$ENV_FILE" ]]; then
    echo "ERROR: .env.monitoring 파일이 없습니다."
    echo
    echo "다음 명령으로 생성하세요:"
    echo "cp .env.monitoring.example .env.monitoring"
    exit 1
fi

# 환경변수 로드
set -a
source "$ENV_FILE"
set +a

# 필수 환경변수 확인
for var in \
    OPENBAKE_CORE_TARGET \
    OPENBAKE_MEMBER_TARGET \
    OPENBAKE_PAYMENT_TARGET
do
    if [[ -z "${!var:-}" ]]; then
        echo "ERROR: ${var} 환경변수가 설정되어 있지 않습니다."
        exit 1
    fi
done

echo
echo "모니터링 대상:"
echo "  Core    : $OPENBAKE_CORE_TARGET"
echo "  Member  : $OPENBAKE_MEMBER_TARGET"
echo "  Payment : $OPENBAKE_PAYMENT_TARGET"

# envsubst 확인
if ! command -v envsubst >/dev/null 2>&1; then
    echo
    echo "ERROR: envsubst 명령이 없습니다."
    echo "설치:"
    echo "sudo apt install -y gettext-base"
    exit 1
fi

# Prometheus 설정 생성
echo
echo "==> Prometheus 설정 생성"

envsubst \
'${OPENBAKE_CORE_TARGET} ${OPENBAKE_MEMBER_TARGET} ${OPENBAKE_PAYMENT_TARGET}' \
< "$TEMPLATE_FILE" \
> "$OUTPUT_FILE"

echo "==> 생성 완료: $OUTPUT_FILE"

# 기존 Prometheus 재실행
echo
echo "==> Prometheus 실행"

docker compose \
    -f "$COMPOSE_FILE" \
    up -d --force-recreate

echo
echo "==> Prometheus 상태"
docker compose \
    -f "$COMPOSE_FILE" \
    ps

echo
echo "========================================"
echo " Monitoring Started"
echo "========================================"
echo "Prometheus : http://localhost:9090"
echo
echo "확인:"
echo "  curl http://localhost:9090/-/healthy"