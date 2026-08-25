#!/usr/bin/env bash
set -Eeuo pipefail

# --------------------------------------------------
# 사용법
# --------------------------------------------------
# 터미널에 붙어 실행하는 foreground 로컬 통합 실행기입니다(백그라운드/nohup으로 띄우는
# 용도가 아닙니다). root -> member-service -> payment-service -> api-gateway 순서로
# 하나씩 health가 UP인지 확인하며 기동하고, 실행한 터미널에서 Ctrl+C로 전부 종료합니다.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

# --------------------------------------------------
# .env 환경변수 로드
# --------------------------------------------------
if [[ -f "$ROOT_DIR/.env" ]]; then
  echo "==> .env 환경변수를 불러옵니다."
  set -a
  source "$ROOT_DIR/.env"
  set +a
else
  echo "WARN: .env 파일이 없습니다."
fi

# --------------------------------------------------
# 기본 설정
# --------------------------------------------------
PROFILE="${SPRING_PROFILES_ACTIVE:-local}"

ROOT_PORT="${ROOT_PORT:-8080}"
MEMBER_PORT="${MEMBER_PORT:-8081}"
PAYMENT_PORT="${PAYMENT_PORT:-8082}"
GATEWAY_PORT="${GATEWAY_PORT:-8089}"

LOG_DIR="$ROOT_DIR/logs/local"
mkdir -p "$LOG_DIR"

PIDS=()

# --------------------------------------------------
# 포트 사용 여부 확인 (사용 중이면 그 자리에서 실패 — 아무 프로세스도 자동 종료하지 않음)
# --------------------------------------------------
check_port_free() {
  local name="$1" port="$2"
  local match
  match="$(ss -ltnp 2>/dev/null | grep ":$port " || true)"
  if [[ -n "$match" ]]; then
    echo "ERROR: $name 포트($port)가 이미 사용 중입니다."
    echo "  $match"
    return 1
  fi
}

# --------------------------------------------------
# health 확인 (최대 90초, 3초 간격). 실패 시 로그 마지막 부분을 민감정보 없이 출력.
# --------------------------------------------------
wait_for_health() {
  local name="$1" port="$2" logfile="$3"
  local max_wait=90 interval=3 waited=0
  echo "==> $name health 확인 중 (최대 ${max_wait}초, http://localhost:$port/actuator/health)"
  while (( waited < max_wait )); do
    if curl -s "http://localhost:$port/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
      echo "==> $name UP (${waited}초)"
      return 0
    fi
    sleep "$interval"
    waited=$((waited + interval))
  done
  echo "ERROR: $name 이(가) ${max_wait}초 내에 UP 상태가 되지 않았습니다."
  echo "==> $logfile 마지막 부분(민감정보 라인 제외):"
  tail -n 80 "$logfile" 2>/dev/null \
    | grep -viE "authorization|jwt_secret|password|accesstoken|refreshtoken|secret" \
    || true
  return 1
}

# --------------------------------------------------
# 종료 처리
# --------------------------------------------------
cleanup() {
  echo
  echo "==> Spring Boot 프로세스를 종료합니다."

  for pid in "${PIDS[@]:-}"; do
    if kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null || true
    fi
  done

  wait 2>/dev/null || true

  echo "==> 종료 완료"
}

trap cleanup EXIT INT TERM

echo "========================================"
echo " OpenBake Local All-in-One Runner"
echo "========================================"
echo "profile        : $PROFILE"
echo "root port      : $ROOT_PORT"
echo "member port    : $MEMBER_PORT"
echo "payment port   : $PAYMENT_PORT"
echo "gateway port   : $GATEWAY_PORT"
echo "logs           : $LOG_DIR"
echo
echo "foreground 실행기입니다 — 이 터미널에 붙어 있어야 하며 Ctrl+C로 전체 종료합니다."
echo

# --------------------------------------------------
# 필수 명령 확인
# --------------------------------------------------
if [[ ! -x "./gradlew" ]]; then
  echo "ERROR: ./gradlew를 찾을 수 없거나 실행 권한이 없습니다."
  echo "프로젝트 루트에 run-all.sh를 두고 실행해 주세요."
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: docker 명령을 찾을 수 없습니다."
  exit 1
fi

# --------------------------------------------------
# 환경변수 확인
# --------------------------------------------------
echo "==> 환경변수 확인"

if [[ -n "${DB_URL:-}" ]]; then
  echo "DB_URL      : 설정됨"
else
  echo "WARN: DB_URL이 설정되어 있지 않습니다."
fi

if [[ -n "${DB_USERNAME:-}" ]]; then
  echo "DB_USERNAME : 설정됨"
else
  echo "WARN: DB_USERNAME이 설정되어 있지 않습니다."
fi

if [[ -n "${JWT_SECRET:-}" ]]; then
  echo "JWT_SECRET  : 설정됨"
else
  echo "WARN: JWT_SECRET이 설정되어 있지 않습니다."
fi

echo

# --------------------------------------------------
# 기존 Docker 애플리케이션 정지
# --------------------------------------------------
echo "==> 기존 Docker 애플리케이션 컨테이너 정지"

docker compose stop \
  backend \
  member-service \
  payment-service \
  nginx \
  certbot 2>/dev/null || true

echo

# --------------------------------------------------
# DB / Redis만 Docker로 실행
# --------------------------------------------------
echo "==> 1/5 Docker 인프라 실행"

docker compose up -d \
  payment-postgres \
  redis \
  postgres \
  member-postgres

echo
echo "==> Docker Compose 상태"
docker compose ps

echo

# --------------------------------------------------
# 기존 포트 사용 여부 확인
# --------------------------------------------------
echo "==> 포트 사용 상태 확인"

check_port_free "Root" "$ROOT_PORT" || exit 1
check_port_free "Member service" "$MEMBER_PORT" || exit 1
check_port_free "Payment service" "$PAYMENT_PORT" || exit 1
check_port_free "API Gateway" "$GATEWAY_PORT" || exit 1

echo "==> $ROOT_PORT / $MEMBER_PORT / $PAYMENT_PORT / $GATEWAY_PORT 포트 사용 가능"
echo

# --------------------------------------------------
# 이전 로그 초기화
# --------------------------------------------------
: > "$LOG_DIR/root.log"
: > "$LOG_DIR/member-service.log"
: > "$LOG_DIR/payment-service.log"
: > "$LOG_DIR/api-gateway.log"

# --------------------------------------------------
# Root 애플리케이션
# --------------------------------------------------
echo "==> 2/5 Root application 실행"

# 접두어 없는 "bootRun"은 멀티모듈(root/member-service/payment-service/api-gateway/
# ai-service) 빌드에서 태스크 해석이 모호해 다른 서브프로젝트의 bootRun이 대신
# 실행될 수 있다(실제로 이 버그로 api-gateway가 대신 뜬 적이 있음). 반드시
# ":bootRun"으로 루트 프로젝트를 명시한다.
./gradlew :bootRun \
  --args="--spring.profiles.active=$PROFILE --server.port=$ROOT_PORT" \
  >"$LOG_DIR/root.log" 2>&1 &

PIDS+=("$!")

echo "    PID=${PIDS[-1]}"
echo "    log=$LOG_DIR/root.log"

wait_for_health "Root" "$ROOT_PORT" "$LOG_DIR/root.log" || exit 1

echo

# --------------------------------------------------
# Member Service
# --------------------------------------------------
echo "==> 3/5 Member service 실행"

./gradlew :member-service:bootRun \
  --args="--spring.profiles.active=$PROFILE --server.port=$MEMBER_PORT" \
  >"$LOG_DIR/member-service.log" 2>&1 &

PIDS+=("$!")

echo "    PID=${PIDS[-1]}"
echo "    log=$LOG_DIR/member-service.log"

wait_for_health "Member service" "$MEMBER_PORT" "$LOG_DIR/member-service.log" || exit 1

echo

# --------------------------------------------------
# Payment Service
# --------------------------------------------------
echo "==> 4/5 Payment service 실행"

./gradlew :payment-service:bootRun \
  --args="--spring.profiles.active=$PROFILE --server.port=$PAYMENT_PORT" \
  >"$LOG_DIR/payment-service.log" 2>&1 &

PIDS+=("$!")

echo "    PID=${PIDS[-1]}"
echo "    log=$LOG_DIR/payment-service.log"

wait_for_health "Payment service" "$PAYMENT_PORT" "$LOG_DIR/payment-service.log" || exit 1

echo

# --------------------------------------------------
# API Gateway (native) — 프론트가 실제로 호출하는 단일 진입점(:8089)
# --------------------------------------------------
echo "==> 5/5 API Gateway 실행"

./gradlew :api-gateway:bootRun \
  --args="--spring.profiles.active=$PROFILE --server.port=$GATEWAY_PORT --openbake.security.gateway-jwt-enabled=true" \
  >"$LOG_DIR/api-gateway.log" 2>&1 &

PIDS+=("$!")

echo "    PID=${PIDS[-1]}"
echo "    log=$LOG_DIR/api-gateway.log"

wait_for_health "API Gateway" "$GATEWAY_PORT" "$LOG_DIR/api-gateway.log" || exit 1

echo
echo "========================================"
echo " 실행을 시작했습니다."
echo "========================================"

echo "Root     : http://localhost:$ROOT_PORT"
echo "Member   : http://localhost:$MEMBER_PORT"
echo "Payment  : http://localhost:$PAYMENT_PORT"
echo "Gateway  : http://localhost:$GATEWAY_PORT"
echo
echo "Frontend API base(NEXT_PUBLIC_API_BASE_URL)는 Gateway 하나만 가리켜야 합니다:"
echo "  http://localhost:$GATEWAY_PORT"

echo
echo "로그 확인:"
echo "  tail -f $LOG_DIR/root.log"
echo "  tail -f $LOG_DIR/member-service.log"
echo "  tail -f $LOG_DIR/payment-service.log"
echo "  tail -f $LOG_DIR/api-gateway.log"

echo
echo "에러만 확인:"
echo "  grep -n -E \"APPLICATION FAILED|Caused by|Exception|ERROR|Port\" \\"
echo "    $LOG_DIR/root.log \\"
echo "    $LOG_DIR/member-service.log \\"
echo "    $LOG_DIR/payment-service.log \\"
echo "    $LOG_DIR/api-gateway.log"

echo
echo "종료하려면 Ctrl+C"
echo

# --------------------------------------------------
# 하나라도 종료되면 전체 종료
# --------------------------------------------------
wait -n "${PIDS[@]}"

echo
echo "ERROR: Spring Boot 프로세스 중 하나가 종료되었습니다."
echo
echo "에러 로그를 확인하세요:"
echo
echo "grep -n -E \"APPLICATION FAILED|Caused by|Exception|ERROR|Port\" \\"
echo "  $LOG_DIR/root.log \\"
echo "  $LOG_DIR/member-service.log \\"
echo "  $LOG_DIR/payment-service.log \\"
echo "  $LOG_DIR/api-gateway.log"

exit 1