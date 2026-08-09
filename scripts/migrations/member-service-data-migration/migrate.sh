#!/usr/bin/env bash
# ============================================================================
# member/auths 데이터를 기존 공유 DB(openbake)에서 member-postgres(openbake_member)로 이전한다.
#
# 사전 조건:
#   - docker compose로 postgres, member-postgres 둘 다 접근 가능해야 한다
#     (member-postgres는 호스트 포트가 안 열려있어서 docker compose exec로만 접근)
#   - 소스(openbake) DB는 로컬 5432 포트로 접근 가능해야 한다(docker-compose.yaml 기본값)
#
# 사용법:
#   ./scripts/migrations/member-service-data-migration/migrate.sh
#
# 이미 이전된 상태에서 다시 실행하면 목적지에 데이터가 있다는 걸 감지하고
# 안전하게 중단한다(중복 삽입 방지). 강제로 다시 하고 싶으면 --force.
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

SOURCE_HOST="${SOURCE_HOST:-localhost}"
SOURCE_PORT="${SOURCE_PORT:-5432}"
SOURCE_DB="${SOURCE_DB:-openbake}"
SOURCE_USER="${SOURCE_USER:-openbake}"
SOURCE_PASSWORD="${SOURCE_PASSWORD:-openbake}"

DEST_SERVICE="${DEST_SERVICE:-member-postgres}"   # docker-compose 서비스명
DEST_DB="${DEST_DB:-openbake_member}"
DEST_USER="${DEST_USER:-openbake}"

FORCE=0
if [[ "${1:-}" == "--force" ]]; then
  FORCE=1
fi

log() { echo "[migrate] $*"; }
psql_source() { PGPASSWORD="$SOURCE_PASSWORD" psql -h "$SOURCE_HOST" -p "$SOURCE_PORT" -U "$SOURCE_USER" -d "$SOURCE_DB" -v ON_ERROR_STOP=1 "$@"; }
psql_dest() { docker compose exec -T "$DEST_SERVICE" psql -U "$DEST_USER" -d "$DEST_DB" -v ON_ERROR_STOP=1 "$@"; }

log "1/6 member-postgres 컨테이너 기동 확인"
docker compose up -d "$DEST_SERVICE"
for _ in $(seq 1 30); do
  if docker compose exec -T "$DEST_SERVICE" pg_isready -U "$DEST_USER" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
if ! docker compose exec -T "$DEST_SERVICE" pg_isready -U "$DEST_USER" >/dev/null 2>&1; then
  echo "[migrate] ERROR: $DEST_SERVICE 가 30초 안에 준비되지 않았습니다." >&2
  exit 1
fi

log "2/6 목적지 데이터 존재 여부 확인"
existing=$(psql_dest -tAc "SELECT count(*) FROM members;" 2>/dev/null || echo "0")
if [[ "$existing" != "0" && "$FORCE" -ne 1 ]]; then
  echo "[migrate] ERROR: openbake_member.members 에 이미 데이터(${existing}건)가 있습니다." >&2
  echo "[migrate]        중복 삽입을 막기 위해 중단합니다. 다시 하려면 --force 로 실행하세요" >&2
  echo "[migrate]        (--force 는 스키마만 재적용할 뿐 기존 행을 지우지 않으니, 정말 새로 하려면 직접 TRUNCATE 먼저 하세요)." >&2
  exit 1
fi

log "3/6 목적지에 스키마 적용 (이미 있으면 건너뜀 - IF NOT EXISTS)"
cat "$SCRIPT_DIR/schema.sql" | psql_dest

log "4/6 소스에서 데이터만 덤프해서 목적지로 복사 (members, auths)"
PGPASSWORD="$SOURCE_PASSWORD" pg_dump -h "$SOURCE_HOST" -p "$SOURCE_PORT" -U "$SOURCE_USER" -d "$SOURCE_DB" \
  --data-only --disable-triggers -t members -t auths \
  | psql_dest

log "5/6 시퀀스 재조정 (members identity, auths_seq)"
psql_dest -c "
SELECT setval(pg_get_serial_sequence('members', 'id'), COALESCE((SELECT MAX(id) FROM members), 1));
SELECT setval('auths_seq', COALESCE((SELECT MAX(id) FROM auths), 1));
"

log "6/6 검증 - 소스/목적지 행 개수 비교"
src_members=$(psql_source -tAc "SELECT count(*) FROM members;")
dst_members=$(psql_dest -tAc "SELECT count(*) FROM members;")
src_auths=$(psql_source -tAc "SELECT count(*) FROM auths;")
dst_auths=$(psql_dest -tAc "SELECT count(*) FROM auths;")

echo "members: source=$src_members dest=$dst_members"
echo "auths:   source=$src_auths dest=$dst_auths"

if [[ "$src_members" != "$dst_members" || "$src_auths" != "$dst_auths" ]]; then
  echo "[migrate] ERROR: 행 개수가 일치하지 않습니다. 위 로그를 확인하세요." >&2
  exit 1
fi

log "완료. member-service의 DB_URL을 member-postgres로 돌린 뒤 재기동해서 로그인 등 실제 동작을 확인하세요."
log "기존 공유 DB(openbake)의 members/auths 는 문제없이 며칠 운영해본 뒤 정리를 권장합니다(바로 지우지 마세요)."
