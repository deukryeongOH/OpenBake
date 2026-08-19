#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ENV_FILE="$SCRIPT_DIR/.env.monitoring"
TEMPLATE_FILE="$SCRIPT_DIR/prometheus/prometheus.yml.template"
OUTPUT_FILE="$SCRIPT_DIR/prometheus/prometheus.yml"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.monitoring.yml"

log() { printf '%s\n' "$*"; }

require_command() {
    local name="$1"
    if ! command -v "$name" >/dev/null 2>&1; then
        echo "ERROR: ${name} 명령을 찾을 수 없습니다."
        exit 1
    fi
}

resolve_target_host() {
    local configured="${OPENBAKE_TARGET_HOST:-auto}"

    if [[ -n "$configured" && "$configured" != "auto" ]]; then
        printf '%s' "$configured"
        return
    fi

    # WSL에서는 Docker Desktop의 host.docker.internal이 Windows 호스트를
    # 가리킬 수 있으므로 WSL의 실제 IPv4 주소를 사용합니다.
    if grep -qiE '(microsoft|wsl)' /proc/sys/kernel/osrelease 2>/dev/null; then
        local wsl_ip
        wsl_ip="$(hostname -I 2>/dev/null | tr ' ' '\n' | grep -E '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$' | head -n 1 || true)"
        if [[ -z "$wsl_ip" ]]; then
            echo "ERROR: WSL IP 자동 탐지에 실패했습니다." >&2
            echo "OPENBAKE_TARGET_HOST를 직접 설정하세요." >&2
            exit 1
        fi
        printf '%s' "$wsl_ip"
        return
    fi

    printf '%s' 'host.docker.internal'
}

resolve_target() {
    local explicit="$1"
    local host="$2"
    local port="$3"

    if [[ -n "$explicit" ]]; then
        # Phase 2의 기존 .env.monitoring에 host.docker.internal:*가 남아 있어도
        # WSL auto-detect가 활성화된 경우에는 감지한 WSL host로 자동 치환합니다.
        if [[ "${OPENBAKE_TARGET_HOST:-auto}" == "auto" && "$host" != "host.docker.internal" && "$explicit" == host.docker.internal:* ]]; then
            printf '%s:%s' "$host" "${explicit##*:}"
        else
            printf '%s' "$explicit"
        fi
    else
        printf '%s:%s' "$host" "$port"
    fi
}

wait_http() {
    local url="$1"
    local name="$2"
    local attempts="${3:-30}"

    for ((i=1; i<=attempts; i++)); do
        if curl -fsS "$url" >/dev/null 2>&1; then
            echo "OK: ${name}"
            return 0
        fi
        sleep 1
    done

    echo "ERROR: ${name}가 준비되지 않았습니다: ${url}"
    return 1
}

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

require_command docker
require_command curl
require_command envsubst

set -a
source "$ENV_FILE"
set +a

: "${OPENBAKE_CORE_PORT:=8080}"
: "${OPENBAKE_MEMBER_PORT:=8081}"
: "${OPENBAKE_PAYMENT_PORT:=8082}"

for var in GRAFANA_ADMIN_USER GRAFANA_ADMIN_PASSWORD; do
    if [[ -z "${!var:-}" ]]; then
        echo "ERROR: ${var} 환경변수가 설정되어 있지 않습니다."
        exit 1
    fi
done

if [[ "$GRAFANA_ADMIN_PASSWORD" == "CHANGE_ME_STRONG_PASSWORD" ]]; then
    echo "ERROR: GRAFANA_ADMIN_PASSWORD 기본값을 실제 비밀번호로 변경하세요."
    exit 1
fi

TARGET_HOST="$(resolve_target_host)"
OPENBAKE_CORE_TARGET="$(resolve_target "${OPENBAKE_CORE_TARGET:-}" "$TARGET_HOST" "$OPENBAKE_CORE_PORT")"
OPENBAKE_MEMBER_TARGET="$(resolve_target "${OPENBAKE_MEMBER_TARGET:-}" "$TARGET_HOST" "$OPENBAKE_MEMBER_PORT")"
OPENBAKE_PAYMENT_TARGET="$(resolve_target "${OPENBAKE_PAYMENT_TARGET:-}" "$TARGET_HOST" "$OPENBAKE_PAYMENT_PORT")"

export OPENBAKE_CORE_TARGET OPENBAKE_MEMBER_TARGET OPENBAKE_PAYMENT_TARGET

log ""
log "모니터링 대상:"
log "  Target host : $TARGET_HOST"
log "  Core        : $OPENBAKE_CORE_TARGET"
log "  Member      : $OPENBAKE_MEMBER_TARGET"
log "  Payment     : $OPENBAKE_PAYMENT_TARGET"

log ""
log "==> Prometheus 설정 생성"
envsubst \
'${OPENBAKE_CORE_TARGET} ${OPENBAKE_MEMBER_TARGET} ${OPENBAKE_PAYMENT_TARGET}' \
< "$TEMPLATE_FILE" \
> "$OUTPUT_FILE"
log "==> 생성 완료: $OUTPUT_FILE"

log ""
log "==> Prometheus + Grafana 실행"
docker compose \
    --env-file "$ENV_FILE" \
    -f "$COMPOSE_FILE" \
    up -d --force-recreate

log ""
log "==> 서비스 준비 대기"
wait_http "http://localhost:9090/-/ready" "Prometheus"
wait_http "http://localhost:3001/api/health" "Grafana"

log ""
log "==> Prometheus 컨테이너 -> Core Actuator 연결 확인"
if docker exec openbake-prometheus \
    wget -q --spider "http://${OPENBAKE_CORE_TARGET}/actuator/prometheus"; then
    log "OK: Core /actuator/prometheus"
else
    log "WARN: Core 연결 실패: http://${OPENBAKE_CORE_TARGET}/actuator/prometheus"
    log "      Core가 실행 중인지 확인하세요."
fi

log ""
log "==> 컨테이너 상태"
docker compose \
    --env-file "$ENV_FILE" \
    -f "$COMPOSE_FILE" \
    ps

log ""
log "========================================"
log " Monitoring Started"
log "========================================"
log "Prometheus : http://localhost:9090"
log "Grafana    : http://localhost:3001"
log ""
log "상태 확인:"
log "  ./verify-monitoring.sh"
