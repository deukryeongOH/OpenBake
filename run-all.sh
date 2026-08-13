#!/usr/bin/env bash
set -Eeuo pipefail

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

LOG_DIR="$ROOT_DIR/logs/local"
mkdir -p "$LOG_DIR"

PIDS=()

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
echo "logs           : $LOG_DIR"
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
  echo "DB_URL      : $DB_URL"
else
  echo "WARN: DB_URL이 설정되어 있지 않습니다."
fi

if [[ -n "${DB_USERNAME:-}" ]]; then
  echo "DB_USERNAME : $DB_USERNAME"
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
echo "==> 1/4 Docker 인프라 실행"

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

if ss -lnt 2>/dev/null | grep -q ":$ROOT_PORT "; then
  echo "ERROR: $ROOT_PORT 포트가 이미 사용 중입니다."
  exit 1
fi

if ss -lnt 2>/dev/null | grep -q ":$MEMBER_PORT "; then
  echo "ERROR: $MEMBER_PORT 포트가 이미 사용 중입니다."
  exit 1
fi

if ss -lnt 2>/dev/null | grep -q ":$PAYMENT_PORT "; then
  echo "ERROR: $PAYMENT_PORT 포트가 이미 사용 중입니다."
  exit 1
fi

echo "==> 8080 / 8081 / 8082 포트 사용 가능"
echo

# --------------------------------------------------
# 이전 로그 초기화
# --------------------------------------------------
: > "$LOG_DIR/root.log"
: > "$LOG_DIR/member-service.log"
: > "$LOG_DIR/payment-service.log"

# --------------------------------------------------
# Root 애플리케이션
# --------------------------------------------------
echo "==> 2/4 Root application 실행"

./gradlew bootRun \
  --args="--spring.profiles.active=$PROFILE --server.port=$ROOT_PORT" \
  >"$LOG_DIR/root.log" 2>&1 &

PIDS+=("$!")

echo "    PID=${PIDS[-1]}"
echo "    log=$LOG_DIR/root.log"

echo

# --------------------------------------------------
# Member Service
# --------------------------------------------------
echo "==> 3/4 Member service 실행"

./gradlew :member-service:bootRun \
  --args="--spring.profiles.active=$PROFILE --server.port=$MEMBER_PORT" \
  >"$LOG_DIR/member-service.log" 2>&1 &

PIDS+=("$!")

echo "    PID=${PIDS[-1]}"
echo "    log=$LOG_DIR/member-service.log"

echo

# --------------------------------------------------
# Payment Service
# --------------------------------------------------
echo "==> 4/4 Payment service 실행"

./gradlew :payment-service:bootRun \
  --args="--spring.profiles.active=$PROFILE --server.port=$PAYMENT_PORT" \
  >"$LOG_DIR/payment-service.log" 2>&1 &

PIDS+=("$!")

echo "    PID=${PIDS[-1]}"
echo "    log=$LOG_DIR/payment-service.log"

echo
echo "========================================"
echo " 실행을 시작했습니다."
echo "========================================"

echo "Root    : http://localhost:$ROOT_PORT"
echo "Member  : http://localhost:$MEMBER_PORT"
echo "Payment : http://localhost:$PAYMENT_PORT"

echo
echo "로그 확인:"
echo "  tail -f $LOG_DIR/root.log"
echo "  tail -f $LOG_DIR/member-service.log"
echo "  tail -f $LOG_DIR/payment-service.log"

echo
echo "에러만 확인:"
echo "  grep -n -E \"APPLICATION FAILED|Caused by|Exception|ERROR|Port\" \\"
echo "    $LOG_DIR/root.log \\"
echo "    $LOG_DIR/member-service.log \\"
echo "    $LOG_DIR/payment-service.log"

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
echo "  $LOG_DIR/payment-service.log"

exit 1