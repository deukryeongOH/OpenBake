#!/usr/bin/env bash
# Compose 쓰기 동결 + 4개 PostgreSQL(core/member/payment/ai) final dump.
# 설계 근거: docs/k3s-learning/12-compose-to-k3s-cutover-rollback-plan.md 4장
#           docs/k3s-learning/issue-5-implementation-prompt.md 작업 2
#
# 이 스크립트는 각 단계 전에 무엇을 할지 출력하고 사람의 확인(read -r -p)을 받는다.
# Go/No-Go는 판정하지 않는다 — 결과를 출력만 하고 사람이 읽고 결정한다.
set -Eeuo pipefail

# ---------------------------------------------------------------------------
# 설정 — 값은 전부 환경 변수로 주입한다. 비밀번호를 이 파일에 하드코딩하지 않는다.
# ---------------------------------------------------------------------------
DB_KEYS=(CORE MEMBER PAYMENT AI)

declare -A DEFAULT_PORT=( [CORE]=5432 [MEMBER]=5434 [PAYMENT]=5435 [AI]=5436 )
declare -A DEFAULT_NAME=( [CORE]=openbake [MEMBER]=openbake_member [PAYMENT]=openbake_payment [AI]=openbake_ai )
declare -A DEFAULT_USER=( [CORE]=openbake [MEMBER]=openbake [PAYMENT]=openbake [AI]="" )

: "${DUMP_DIR:=/opt/openbake/cutover/dumps/$(date +%Y%m%dT%H%M%S)}"
: "${COMPOSE_FILE:=docker-compose.yaml:docker-compose.prod.yaml}"
: "${S3_BUCKET:=}"
: "${AWS_REGION:=ap-northeast-2}"

MANIFEST="$DUMP_DIR/manifest.txt"
LOG="$DUMP_DIR/freeze-and-dump.log"

log() { echo "[$(date '+%H:%M:%S')] $*" | tee -a "$LOG"; }

confirm() {
    local prompt="$1"
    local answer
    read -r -p "$prompt (진행하려면 yes 입력): " answer
    if [[ "$answer" != "yes" ]]; then
        log "사람이 진행을 중단했다: $prompt"
        exit 1
    fi
}

require_env() {
    local var="$1"
    if [[ -z "${!var:-}" ]]; then
        echo "필수 환경 변수 $var 가 비어 있다. README.md의 '공통 환경 변수' 표를 참고해 설정하라." >&2
        exit 1
    fi
}

# ---------------------------------------------------------------------------
# STEP 0. 사전 점검
# ---------------------------------------------------------------------------
for tool in pg_dump pg_restore sha256sum docker; do
    command -v "$tool" >/dev/null 2>&1 || { echo "$tool 이 설치되어 있지 않다." >&2; exit 1; }
done

PG_DUMP_VERSION="$(pg_dump --version)"
log "pg_dump 버전: $PG_DUMP_VERSION"
if [[ "$PG_DUMP_VERSION" != *") 17."* ]]; then
    echo "pg_dump가 PostgreSQL 17 client가 아니다: $PG_DUMP_VERSION" >&2
    confirm "PostgreSQL 17이 아닌 client로 계속 진행하겠는가"
fi

for key in "${DB_KEYS[@]}"; do
    require_env "${key}_COMPOSE_DB_PASSWORD"
    if [[ -z "${DEFAULT_USER[$key]}" ]]; then
        require_env "${key}_COMPOSE_DB_USER"
    fi
done

mkdir -p "$DUMP_DIR"
chmod 700 "$DUMP_DIR"
: > "$MANIFEST"
: > "$LOG"

# COMPOSE_FILE="a.yaml:b.yaml" → docker compose --file a.yaml --file b.yaml
IFS=':' read -r -a _compose_files <<< "$COMPOSE_FILE"
COMPOSE_ARGS=()
for f in "${_compose_files[@]}"; do
    COMPOSE_ARGS+=(--file "$f")
done

log "dump 디렉터리: $DUMP_DIR (권한 700)"

# ---------------------------------------------------------------------------
# STEP 1. 점검 공지·외부 요청 차단 확인 (사람이 별도로 수행)
# ---------------------------------------------------------------------------
echo
echo "=== STEP 1. 점검 공지·외부 요청 차단 ==="
echo "점검 공지를 발송하고 신규 외부 요청을 차단했는지 확인하라 (LB/DNS/공지 페이지 등, 이 스크립트 밖에서 수행)."
confirm "점검 공지와 외부 요청 차단을 완료했는가"

# ---------------------------------------------------------------------------
# STEP 2. 신규 결제 흐름 중단 확인
# ---------------------------------------------------------------------------
echo
echo "=== STEP 2. 결제 흐름 중단 확인 ==="
echo "신규 결제 요청을 막고, 처리 중인 결제가 남아 있지 않은지 Toss 대시보드/payment-service 로그로 확인하라."
confirm "처리 중인 결제가 없음을 확인했는가"

# ---------------------------------------------------------------------------
# STEP 3. Compose 애플리케이션 중지
# ---------------------------------------------------------------------------
echo
echo "=== STEP 3. 애플리케이션 중지 ==="
echo "중지 대상: api-gateway, backend, member-service, payment-service, ai-service"
echo "PostgreSQL 4개 container는 dump를 위해 계속 실행한다."
echo
echo "ai-service는 12번 문서 4.1장에 없지만, Kafka consumer로 ai-postgres에 write하므로"
echo "이번 절차에서는 write 가능한 애플리케이션으로 취급해 함께 중지한다."
confirm "위 5개 애플리케이션을 지금 중지하겠는가"

docker compose "${COMPOSE_ARGS[@]}" stop \
    api-gateway backend member-service payment-service ai-service
log "애플리케이션 5개 중지 완료"

# ---------------------------------------------------------------------------
# STEP 4. write 중단 확인
# ---------------------------------------------------------------------------
echo
echo "=== STEP 4. write 중단 확인 ==="
docker compose "${COMPOSE_ARGS[@]}" ps
echo
echo "위 목록에서 api-gateway/backend/member-service/payment-service/ai-service가 모두 중지 상태인지 확인하라."
confirm "애플리케이션 write가 더 이상 발생하지 않음을 확인했는가"

# ---------------------------------------------------------------------------
# STEP 5. DB별 final dump
# ---------------------------------------------------------------------------
echo
echo "=== STEP 5. PostgreSQL final dump (core → member → payment → ai) ==="
echo "형식: pg_dump -Fc --no-owner --no-privileges"
confirm "4개 DB의 dump를 지금 시작하겠는가"

for key in "${DB_KEYS[@]}"; do
    host_var="${key}_COMPOSE_DB_HOST"
    port_var="${key}_COMPOSE_DB_PORT"
    name_var="${key}_COMPOSE_DB_NAME"
    user_var="${key}_COMPOSE_DB_USER"
    pass_var="${key}_COMPOSE_DB_PASSWORD"

    host="${!host_var:-127.0.0.1}"
    port="${!port_var:-${DEFAULT_PORT[$key]}}"
    dbname="${!name_var:-${DEFAULT_NAME[$key]}}"
    user="${!user_var:-${DEFAULT_USER[$key]}}"

    dump_file="$DUMP_DIR/${key,,}.dump"

    log "[$key] dump 시작 — host=$host port=$port db=$dbname user=$user"
    start_ts="$(date '+%Y-%m-%dT%H:%M:%S%z')"

    PGPASSWORD="${!pass_var}" pg_dump -Fc --no-owner --no-privileges \
        -h "$host" -p "$port" -U "$user" -d "$dbname" -f "$dump_file"

    end_ts="$(date '+%Y-%m-%dT%H:%M:%S%z')"

    # dump 무결성 확인 — pg_restore --list가 실패하면 dump가 깨진 것이다.
    pg_restore --list "$dump_file" >/dev/null

    size_bytes="$(stat -c%s "$dump_file" 2>/dev/null || stat -f%z "$dump_file")"
    checksum="$(sha256sum "$dump_file" | awk '{print $1}')"

    printf '%s\tstart=%s\tend=%s\tsize_bytes=%s\tsha256=%s\tfile=%s\n' \
        "$key" "$start_ts" "$end_ts" "$size_bytes" "$checksum" "$dump_file" | tee -a "$MANIFEST"

    log "[$key] dump 완료 — size=${size_bytes}B checksum=$checksum"
done

# ---------------------------------------------------------------------------
# STEP 6. S3 업로드 (선택)
# ---------------------------------------------------------------------------
echo
echo "=== STEP 6. private S3 업로드 ==="
if [[ -z "$S3_BUCKET" ]]; then
    echo "S3_BUCKET이 설정되지 않았다. 이 단계는 건너뛴다 — dump 파일을 수동으로 private S3에 업로드하라."
    echo "대상 파일: $DUMP_DIR/*.dump, $MANIFEST"
else
    confirm "s3://$S3_BUCKET/cutover-final/$(basename "$DUMP_DIR")/ 로 dump 파일을 업로드하겠는가"
    command -v aws >/dev/null 2>&1 || { echo "aws CLI가 설치되어 있지 않다." >&2; exit 1; }
    for key in "${DB_KEYS[@]}"; do
        dump_file="$DUMP_DIR/${key,,}.dump"
        key_path="cutover-final/$(basename "$DUMP_DIR")/${key,,}.dump"
        aws s3 cp "$dump_file" "s3://$S3_BUCKET/$key_path" --region "$AWS_REGION"
        log "[$key] S3 업로드 완료 — s3://$S3_BUCKET/$key_path"
    done
    aws s3 cp "$MANIFEST" "s3://$S3_BUCKET/cutover-final/$(basename "$DUMP_DIR")/manifest.txt" --region "$AWS_REGION"
fi

echo
echo "=== 완료 ==="
echo "manifest: $MANIFEST"
cat "$MANIFEST"
echo
echo "다음 단계: ./02-restore-and-verify.sh (DUMP_DIR=$DUMP_DIR 를 그대로 넘겨서 실행)"
