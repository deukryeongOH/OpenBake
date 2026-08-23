#!/usr/bin/env bash
# k3s PostgreSQL(core/member/payment/ai) restore + verify-queries.sql 실행.
# 설계 근거: docs/k3s-learning/12-compose-to-k3s-cutover-rollback-plan.md 4.3장·5장
#           docs/k3s-learning/issue-5-implementation-prompt.md 작업 3
#
# pg_restore의 exit code만으로 Go를 판단하지 않는다 — verify-queries.sql 결과를
# 출력하고, Compose 쪽 결과와 맞대어 보는 것은 사람이 한다.
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

DB_KEYS=(CORE MEMBER PAYMENT AI)
declare -A DEFAULT_NAME=( [CORE]=openbake [MEMBER]=openbake_member [PAYMENT]=openbake_payment [AI]=openbake_ai )
declare -A SVC_NAME=( [CORE]=core-postgres [MEMBER]=member-postgres [PAYMENT]=payment-postgres [AI]=ai-postgres )
declare -A LOCAL_PORT_OFFSET=( [CORE]=0 [MEMBER]=1 [PAYMENT]=2 [AI]=3 )

: "${DUMP_DIR:?DUMP_DIR를 01-freeze-and-dump.sh가 만든 디렉터리로 지정하라}"
: "${KUBE_NAMESPACE:=openbake}"
: "${PORT_FORWARD_BASE_PORT:=15432}"

RESULT_DIR="$DUMP_DIR/restore"
mkdir -p "$RESULT_DIR"
LOG="$RESULT_DIR/restore-and-verify.log"
: > "$LOG"

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

PF_PID=""
close_port_forward() {
    if [[ -n "$PF_PID" ]] && kill -0 "$PF_PID" 2>/dev/null; then
        kill "$PF_PID" 2>/dev/null || true
        wait "$PF_PID" 2>/dev/null || true
    fi
    PF_PID=""
}
trap close_port_forward EXIT

for tool in kubectl psql pg_restore; do
    command -v "$tool" >/dev/null 2>&1 || { echo "$tool 이 설치되어 있지 않다." >&2; exit 1; }
done

echo "=== 사전 확인 ==="
echo "현재 kubectl context: $(kubectl config current-context)"
confirm "이 context가 대상 k3s 클러스터가 맞는가"

for key in "${DB_KEYS[@]}"; do
    svc="${SVC_NAME[$key]}"
    dbname_default="${DEFAULT_NAME[$key]}"
    local_port=$((PORT_FORWARD_BASE_PORT + LOCAL_PORT_OFFSET[$key]))
    dump_file="$DUMP_DIR/${key,,}.dump"
    restore_log="$RESULT_DIR/${key,,}-restore.log"
    verify_out="$RESULT_DIR/${key,,}-verify.txt"

    if [[ ! -f "$dump_file" ]]; then
        echo "[$key] dump 파일이 없다: $dump_file" >&2
        exit 1
    fi

    echo
    echo "=== [$key] Pod Ready 확인 ==="
    kubectl -n "$KUBE_NAMESPACE" get pod -l "app.kubernetes.io/name=$svc" -o wide
    confirm "[$key] Pod가 계획한 Node에서 Ready인가"

    log "[$key] 자격 증명을 k8s Secret ${key,,}-db-credentials 에서 읽는다"
    db_user="$(kubectl -n "$KUBE_NAMESPACE" get secret "${key,,}-db-credentials" -o jsonpath='{.data.DB_USERNAME}' | base64 -d)"
    db_pass="$(kubectl -n "$KUBE_NAMESPACE" get secret "${key,,}-db-credentials" -o jsonpath='{.data.DB_PASSWORD}' | base64 -d)"
    dbname="$dbname_default"

    echo "=== [$key] port-forward 시작 (127.0.0.1:$local_port -> $svc:5432) ==="
    kubectl -n "$KUBE_NAMESPACE" port-forward "svc/$svc" "$local_port:5432" >"$RESULT_DIR/${key,,}-port-forward.log" 2>&1 &
    PF_PID=$!
    sleep 3
    if ! kill -0 "$PF_PID" 2>/dev/null; then
        echo "[$key] port-forward가 즉시 종료됐다. 로그 확인: $RESULT_DIR/${key,,}-port-forward.log" >&2
        exit 1
    fi

    echo "=== [$key] 대상 DB가 신규·빈 상태인지 확인 ==="
    table_count="$(PGPASSWORD="$db_pass" psql -h 127.0.0.1 -p "$local_port" -U "$db_user" -d "$dbname" -tAc \
        "SELECT count(*) FROM information_schema.tables WHERE table_schema='public'")"
    echo "[$key] 현재 public 스키마 테이블 수: $table_count"
    if [[ "$table_count" -gt 0 ]]; then
        echo "[$key] 테이블이 이미 존재한다. 재실행/재검증 상황이 아니라면 중단을 검토하라."
    fi
    confirm "[$key] 이 상태에서 restore를 진행하겠는가"

    if [[ "$key" == "AI" ]]; then
        echo "=== [$key] pgvector extension 준비 ==="
        confirm "[$key] CREATE EXTENSION IF NOT EXISTS vector; 를 실행하겠는가"
        PGPASSWORD="$db_pass" psql -h 127.0.0.1 -p "$local_port" -U "$db_user" -d "$dbname" \
            -c "CREATE EXTENSION IF NOT EXISTS vector;"
    fi

    echo "=== [$key] restore 시작 ==="
    confirm "[$key] $dump_file 을 지금 restore 하겠는가"
    start_ts="$(date '+%Y-%m-%dT%H:%M:%S%z')"
    PGPASSWORD="$db_pass" pg_restore --no-owner --no-privileges \
        -h 127.0.0.1 -p "$local_port" -U "$db_user" -d "$dbname" \
        "$dump_file" > "$restore_log" 2>&1 || true
    end_ts="$(date '+%Y-%m-%dT%H:%M:%S%z')"
    log "[$key] restore 완료 (start=$start_ts end=$end_ts) — log: $restore_log"

    if grep -qi "error" "$restore_log"; then
        echo "[$key] restore log에 error가 있다. 아래 내용을 확인하라:"
        grep -i "error" "$restore_log"
    else
        echo "[$key] restore log에 error 없음"
    fi

    echo "=== [$key] verify-queries.sql 실행 ==="
    PGPASSWORD="$db_pass" psql -h 127.0.0.1 -p "$local_port" -U "$db_user" -d "$dbname" \
        -v ON_ERROR_STOP=0 -f "$SCRIPT_DIR/verify-queries.sql" | tee "$verify_out"

    echo
    echo "[$key] 위 결과를 Compose 쪽에서 같은 쿼리를 실행한 결과와 비교하라: $verify_out"
    confirm "[$key] 검증 결과를 확인했고 다음 DB로 진행하겠는가"

    close_port_forward
done

echo
echo "=== 완료 ==="
echo "복원 순서: core → member → payment → ai (문제 구분이 쉬운 순차 복원, 첫 전환은 병렬화하지 않음)"
echo "결과 디렉터리: $RESULT_DIR"
echo "다음 단계: Elasticsearch 재색인(POST /internal/v1/products/reindex) 후 ./smoke-test.sh"
