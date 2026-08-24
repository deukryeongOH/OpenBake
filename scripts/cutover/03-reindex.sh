#!/usr/bin/env bash
# Elasticsearch 전체 재색인을 임의 시점에 한 번 트리거한다.
# 12번 문서 7장: ES data volume을 복사하지 않고 core DB를 원본으로 재색인한다.
#
# 호출 대상: POST /internal/v1/products/reindex (AdminProductReindexController)
#
# 인증 구조 — 두 겹이다.
#   1) 외부 Ingress(k8s/openbake/entrypoint/ingress.yaml)는 /internal/** 경로를 라우팅하지 않는다.
#   2) SecurityConfig.java의 .requestMatchers("/internal/v1/**").hasRole("ADMIN")
#
# 따라서 Gateway를 거치지 않고 backend로 직접 port-forward한 뒤,
# HeaderAuthenticationFilter가 신뢰하는 신원 header 3개를 직접 붙인다.
# 이 경로는 NetworkPolicy와 kubectl 접근 권한으로 보호된다.

set -Eeuo pipefail

KUBE_NAMESPACE="${KUBE_NAMESPACE:-openbake}"
LOCAL_PORT="${LOCAL_PORT:-18080}"
ADMIN_MEMBER_ID="${ADMIN_MEMBER_ID:-}"
REINDEX_TIMEOUT="${REINDEX_TIMEOUT:-1800}"

if [[ -z "$ADMIN_MEMBER_ID" ]]; then
    echo "ERROR: ADMIN_MEMBER_ID가 필요하다. ROLE_ADMIN 회원의 member id를 지정한다." >&2
    echo "예: ADMIN_MEMBER_ID=1 ./03-reindex.sh" >&2
    exit 1
fi

if ! [[ "$ADMIN_MEMBER_ID" =~ ^[1-9][0-9]*$ ]]; then
    echo "ERROR: ADMIN_MEMBER_ID는 1 이상의 정수여야 한다. 입력값=$ADMIN_MEMBER_ID" >&2
    exit 1
fi

echo "========================================"
echo " Elasticsearch 전체 재색인"
echo "========================================"
echo "namespace       : $KUBE_NAMESPACE"
echo "admin member id : $ADMIN_MEMBER_ID"
echo "timeout         : ${REINDEX_TIMEOUT}s"
echo
echo "주의: 재색인이 끝날 때까지 검색을 외부에 열지 않는다(12번 문서 7장)."
echo

read -r -p "재색인을 시작하는가? [y/N] " answer
if [[ ! "$answer" =~ ^[Yy]$ ]]; then
    echo "중단한다."
    exit 0
fi

echo
echo "==> backend Ready 확인"
kubectl -n "$KUBE_NAMESPACE" rollout status deployment/backend --timeout=120s

echo
echo "==> port-forward 시작 (127.0.0.1:${LOCAL_PORT} → backend:8080)"
kubectl -n "$KUBE_NAMESPACE" port-forward deployment/backend "${LOCAL_PORT}:8080" >/dev/null 2>&1 &
pf_pid=$!
trap 'kill "$pf_pid" 2>/dev/null || true' EXIT

for _ in $(seq 1 30); do
    if curl -sS -o /dev/null "http://127.0.0.1:${LOCAL_PORT}/actuator/health" 2>/dev/null; then
        break
    fi
    sleep 1
done

if ! curl -sS -o /dev/null "http://127.0.0.1:${LOCAL_PORT}/actuator/health"; then
    echo "ERROR: port-forward로 backend에 연결하지 못했다." >&2
    exit 1
fi

echo
echo "==> 재색인 요청 (완료까지 응답을 기다린다. 데이터 양에 따라 수 분 이상 걸릴 수 있다)"
started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

http_status="$(curl -sS -o /tmp/reindex-response.json -w '%{http_code}' \
    --max-time "$REINDEX_TIMEOUT" \
    -X POST "http://127.0.0.1:${LOCAL_PORT}/internal/v1/products/reindex" \
    -H "X-Openbake-Member-Id: ${ADMIN_MEMBER_ID}" \
    -H "X-Openbake-Member-Role: ADMIN" \
    -H "X-Openbake-Auth-Source: api-gateway" || echo "000")"

finished_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

echo
echo "HTTP status : $http_status"
echo "시작        : $started_at"
echo "종료        : $finished_at"
echo "응답        :"
cat /tmp/reindex-response.json 2>/dev/null || true
echo

case "$http_status" in
    200)
        echo "OK: 재색인 요청이 성공했다."
        ;;
    401)
        echo "FAIL: 신원 header가 거부됐다. ADMIN_MEMBER_ID가 실제 존재하는 회원인지 확인한다." >&2
        exit 1
        ;;
    403)
        echo "FAIL: ROLE_ADMIN이 아니다. 해당 회원의 권한을 확인한다." >&2
        exit 1
        ;;
    000)
        echo "FAIL: 응답을 받지 못했다(timeout 또는 연결 오류). 재색인이 진행 중일 수 있으므로" >&2
        echo "      backend 로그로 진행 상황을 먼저 확인하고 재실행 여부를 판단한다." >&2
        exit 1
        ;;
    *)
        echo "FAIL: 예상하지 않은 응답이다." >&2
        exit 1
        ;;
esac

echo
echo "다음을 사람이 직접 확인한다 (12번 문서 7장):"
echo "  - index document 수가 core DB의 상품 수와 일치하는가"
echo "  - 대표 검색어 결과가 기대와 맞는가"
echo
echo "확인 후 ./smoke-test.sh 로 진행한다."
