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

host_core_is_reachable() {
    # HTTP status가 401/404여도 "포트에 서버가 있다"는 사실만 확인하면 되므로 -f를 쓰지 않습니다.
    curl -sS -o /dev/null --connect-timeout 1 --max-time 2 \
        "http://127.0.0.1:${OPENBAKE_CORE_PORT}/actuator/health" 2>/dev/null
}

container_is_running() {
    local container="$1"
    [[ "$(docker inspect -f '{{.State.Running}}' "$container" 2>/dev/null || true)" == "true" ]]
}

detect_profile() {
    local requested="${1:-auto}"

    if [[ "$requested" == "local" || "$requested" == "server" ]]; then
        printf '%s' "$requested"
        return
    fi

    if [[ "$requested" != "auto" ]]; then
        echo "ERROR: profile은 auto, local, server 중 하나여야 합니다: $requested" >&2
        exit 1
    fi

    # 로컬 WSL에서는 bootRun 서버가 host 8080에서 실제로 떠 있으므로 local 우선.
    # 서버에서는 Core가 Docker 내부 8080만 사용하고 host 8080은 열리지 않으므로 server 선택.
    if host_core_is_reachable; then
        printf '%s' "local"
        return
    fi

    if container_is_running "$CORE_CONTAINER"; then
        printf '%s' "server"
        return
    fi

    echo "ERROR: 모니터링 환경 자동 감지에 실패했습니다." >&2
    echo "       로컬: Core가 localhost:${OPENBAKE_CORE_PORT}에서 실행 중인지 확인하세요." >&2
    echo "       서버: ${CORE_CONTAINER} 컨테이너가 실행 중인지 확인하세요." >&2
    echo "       또는 ./run-monitoring.sh local|server 로 명시하세요." >&2
    exit 1
}

resolve_local_target_host() {
    local configured="${OPENBAKE_TARGET_HOST:-auto}"

    if [[ -n "$configured" && "$configured" != "auto" ]]; then
        printf '%s' "$configured"
        return
    fi

    # WSL + Docker Desktop:
    # Prometheus 컨테이너가 WSL에서 bootRun 중인 애플리케이션으로 접근할 수 있도록
    # 기존에 잘 동작하던 WSL IPv4 자동 탐지를 그대로 유지합니다.
    if grep -qiE '(microsoft|wsl)' /proc/sys/kernel/osrelease 2>/dev/null; then
        local wsl_ip
        wsl_ip="$(
            hostname -I 2>/dev/null \
            | tr ' ' '\n' \
            | grep -E '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$' \
            | head -n 1 || true
        )"
        if [[ -z "$wsl_ip" ]]; then
            echo "ERROR: WSL IP 자동 탐지에 실패했습니다." >&2
            echo "OPENBAKE_TARGET_HOST를 직접 설정하세요." >&2
            exit 1
        fi
        printf '%s' "$wsl_ip"
        return
    fi

    printf '%s' "host.docker.internal"
}

resolve_local_target() {
    local explicit="$1"
    local host="$2"
    local port="$3"

    if [[ -n "$explicit" ]]; then
        # 기존 .env.monitoring에 host.docker.internal:*가 있어도
        # WSL auto 모드에서는 실제 WSL IP로 치환하여 기존 로컬 동작을 보존합니다.
        if [[ "${OPENBAKE_TARGET_HOST:-auto}" == "auto" \
              && "$host" != "host.docker.internal" \
              && "$explicit" == host.docker.internal:* ]]; then
            printf '%s:%s' "$host" "${explicit##*:}"
        else
            printf '%s' "$explicit"
        fi
    else
        printf '%s:%s' "$host" "$port"
    fi
}

get_application_network() {
    local network
    network="$(
        docker inspect "$CORE_CONTAINER" \
            --format '{{range $name, $cfg := .NetworkSettings.Networks}}{{$name}}{{"\n"}}{{end}}' \
            2>/dev/null \
        | sed '/^$/d' \
        | head -n 1
    )"

    if [[ -z "$network" ]]; then
        echo "ERROR: ${CORE_CONTAINER}의 Docker network를 찾지 못했습니다." >&2
        exit 1
    fi

    printf '%s' "$network"
}

ensure_prometheus_on_network() {
    local network="$1"

    if docker inspect "$PROM_CONTAINER" \
        --format '{{range $name, $cfg := .NetworkSettings.Networks}}{{$name}}{{"\n"}}{{end}}' \
        2>/dev/null \
        | grep -Fxq "$network"; then
        log "OK: ${PROM_CONTAINER}는 이미 ${network}에 연결되어 있습니다."
        return
    fi

    docker network connect "$network" "$PROM_CONTAINER"
    log "OK: ${PROM_CONTAINER} -> ${network} 연결 완료"
}

check_actuator_from_prometheus() {
    local label="$1"
    local target="$2"

    if docker exec "$PROM_CONTAINER" \
        wget -q --spider "http://${target}/actuator/prometheus"; then
        log "OK: ${label} /actuator/prometheus (${target})"
        return 0
    fi

    log "WARN: ${label} 연결 실패: http://${target}/actuator/prometheus"
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

: "${CORE_CONTAINER:=openbake-backend}"
: "${MEMBER_CONTAINER:=openbake-member-service}"
: "${PAYMENT_CONTAINER:=openbake-payment}"
: "${PROM_CONTAINER:=openbake-prometheus}"

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

REQUESTED_PROFILE="${1:-${MONITORING_PROFILE:-auto}}"
PROFILE="$(detect_profile "$REQUESTED_PROFILE")"

if [[ "$PROFILE" == "local" ]]; then
    TARGET_HOST="$(resolve_local_target_host)"

    OPENBAKE_CORE_TARGET="$(
        resolve_local_target \
            "${LOCAL_CORE_TARGET:-${OPENBAKE_CORE_TARGET:-}}" \
            "$TARGET_HOST" \
            "$OPENBAKE_CORE_PORT"
    )"
    OPENBAKE_MEMBER_TARGET="$(
        resolve_local_target \
            "${LOCAL_MEMBER_TARGET:-${OPENBAKE_MEMBER_TARGET:-}}" \
            "$TARGET_HOST" \
            "$OPENBAKE_MEMBER_PORT"
    )"
    OPENBAKE_PAYMENT_TARGET="$(
        resolve_local_target \
            "${LOCAL_PAYMENT_TARGET:-${OPENBAKE_PAYMENT_TARGET:-}}" \
            "$TARGET_HOST" \
            "$OPENBAKE_PAYMENT_PORT"
    )"
else
    for container in "$CORE_CONTAINER" "$MEMBER_CONTAINER" "$PAYMENT_CONTAINER"; do
        if ! container_is_running "$container"; then
            echo "ERROR: 서버 모드에서 필요한 컨테이너가 실행 중이 아닙니다: $container" >&2
            exit 1
        fi
    done

    TARGET_HOST="docker-network"
    OPENBAKE_CORE_TARGET="${SERVER_CORE_TARGET:-${CORE_CONTAINER}:8080}"
    OPENBAKE_MEMBER_TARGET="${SERVER_MEMBER_TARGET:-${MEMBER_CONTAINER}:8081}"
    OPENBAKE_PAYMENT_TARGET="${SERVER_PAYMENT_TARGET:-${PAYMENT_CONTAINER}:8082}"
fi

export OPENBAKE_CORE_TARGET OPENBAKE_MEMBER_TARGET OPENBAKE_PAYMENT_TARGET

log ""
log "실행 모드:"
log "  Profile     : $PROFILE"
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

if [[ "$PROFILE" == "server" ]]; then
    log ""
    log "==> Prometheus를 OpenBake 애플리케이션 Docker network에 연결"
    APP_NETWORK="$(get_application_network)"
    log "  Application network : $APP_NETWORK"
    ensure_prometheus_on_network "$APP_NETWORK"
fi

log ""
log "==> 서비스 준비 대기"
wait_http "http://localhost:9090/-/ready" "Prometheus"
wait_http "http://localhost:3001/api/health" "Grafana"

log ""
log "==> Prometheus 컨테이너 -> Actuator 연결 확인"
FAILED=0
check_actuator_from_prometheus "Core" "$OPENBAKE_CORE_TARGET" || FAILED=1
check_actuator_from_prometheus "Member" "$OPENBAKE_MEMBER_TARGET" || FAILED=1
check_actuator_from_prometheus "Payment" "$OPENBAKE_PAYMENT_TARGET" || FAILED=1

if [[ "$FAILED" -ne 0 ]]; then
    log ""
    log "WARN: 일부 Actuator 연결에 실패했습니다."
    log "      ./verify-monitoring.sh 로 Prometheus target 상태를 확인하세요."
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
log "Profile    : $PROFILE"
log "Prometheus : http://localhost:9090"
log "Grafana    : http://localhost:3001"
log ""
log "상태 확인:"
log "  ./verify-monitoring.sh"
