#!/usr/bin/env bash
# 기동 후 smoke test — 인증/조회/주문(비결제)/결제상태 확인 + 내부 endpoint 노출 차단 확인.
# 설계 근거: docs/k3s-learning/12-compose-to-k3s-cutover-rollback-plan.md 8장
#           docs/k3s-learning/issue-5-implementation-prompt.md 작업 5
#
# 기본값은 kubectl port-forward로 api-gateway에 접근하는 내부(cutover 전) smoke test다.
# --external 을 주면 공인 도메인으로 같은 확인을 반복한다(Traefik/TLS 전환 이후에만 사용).
set -Eeuo pipefail

MODE="internal"
if [[ "${1:-}" == "--external" ]]; then
    MODE="external"
fi

: "${KUBE_NAMESPACE:=openbake}"
: "${GATEWAY_SVC:=api-gateway}"
: "${LOCAL_PORT:=18080}"
# api-gateway Service의 포트. k8s/openbake/apps/api-gateway/service.yaml과 맞춘다.
: "${GATEWAY_SVC_PORT:=8080}"
: "${EXTERNAL_BASE_URL:=}"
: "${SMOKE_TEST_MEMBER_EMAIL:=}"
: "${SMOKE_TEST_MEMBER_PASSWORD:=}"
# 공개 조회 확인에 쓸 실제 id. 게이트웨이의 공개 GET 패턴이 숫자 id만 허용한다
# (PublicEndpointPolicy.java) — 존재하는 id여야 200이 나온다.
: "${SMOKE_TEST_PRODUCT_ID:=11}"
: "${SMOKE_TEST_DROP_ID:=1}"

confirm() {
    local prompt="$1"
    local answer
    read -r -p "$prompt (진행하려면 yes 입력): " answer
    [[ "$answer" == "yes" ]] || { echo "중단함: $prompt"; exit 1; }
}

PF_PID=""
cleanup() {
    if [[ -n "$PF_PID" ]] && kill -0 "$PF_PID" 2>/dev/null; then
        kill "$PF_PID" 2>/dev/null || true
        wait "$PF_PID" 2>/dev/null || true
    fi
}
trap cleanup EXIT

if [[ "$MODE" == "external" ]]; then
    : "${EXTERNAL_BASE_URL:?--external 사용 시 EXTERNAL_BASE_URL(예: https://3.38.24.67.sslip.io)을 지정하라}"
    BASE_URL="$EXTERNAL_BASE_URL"
    echo "=== 외부 대상 smoke test: $BASE_URL ==="
    confirm "Traefik/TLS 전환이 끝난 뒤 실행하는 것이 맞는가"
else
    command -v kubectl >/dev/null 2>&1 || { echo "kubectl이 설치되어 있지 않다." >&2; exit 1; }
    echo "=== kubectl port-forward로 $GATEWAY_SVC 연결 (127.0.0.1:$LOCAL_PORT) ==="
    kubectl -n "$KUBE_NAMESPACE" port-forward "svc/$GATEWAY_SVC" "$LOCAL_PORT:$GATEWAY_SVC_PORT" \
        >/tmp/openbake-smoke-port-forward.log 2>&1 &
    PF_PID=$!
    sleep 3
    kill -0 "$PF_PID" 2>/dev/null || { echo "port-forward 실패, 로그: /tmp/openbake-smoke-port-forward.log" >&2; exit 1; }
    BASE_URL="http://127.0.0.1:$LOCAL_PORT"
fi

PASS=0
FAIL=0

check() {
    local desc="$1" method="$2" path="$3" expect_code="$4"
    shift 4
    local code
    code="$(curl -s -o /dev/null -w '%{http_code}' -X "$method" "$BASE_URL$path" "$@")"
    if [[ "$code" == "$expect_code" ]]; then
        echo "PASS  [$desc] $method $path -> $code"
        PASS=$((PASS + 1))
    else
        echo "FAIL  [$desc] $method $path -> $code (기대값 $expect_code)"
        FAIL=$((FAIL + 1))
    fi
}

echo
if [[ "$MODE" == "external" ]]; then
    echo "=== 노출 차단 확인 — 외부에서 절대 200이 나오면 안 되는 경로 ==="
    check "actuator/prometheus 외부 차단" GET "/actuator/prometheus" 404
    check "actuator/env 외부 차단"        GET "/actuator/env" 404
    check "actuator/metrics 외부 차단"    GET "/actuator/metrics" 404
    check "internal 경로 외부 차단"      GET "/internal/v1/products/reindex" 404
else
    echo "=== 노출 차단 확인은 --external 모드에서만 의미가 있다 (Traefik/Ingress 레벨 차단이라 port-forward로는 검증 안 됨) — 건너뜀 ==="
fi

echo
echo "=== 공개 조회 API ==="
# 게이트웨이가 인증 없이 통과시키는 GET은 아래 두 패턴뿐이다
# (PublicEndpointPolicy.java): ^/api/v1/products/[1-9][0-9]*$
#                              ^/api/v1/drops/[1-9][0-9]*/info$
check "상품 상세 조회" GET "/api/v1/products/$SMOKE_TEST_PRODUCT_ID" 200
check "drop 정보 조회" GET "/api/v1/drops/$SMOKE_TEST_DROP_ID/info" 200

echo
echo "=== 비공개 확인 — 목록성 조회는 인증이 필요하다 ==="
# 2026-08-24: 이전 버전은 아래 둘을 공개 API로 기대해 항상 FAIL이었다.
# 401이 설계대로의 동작이므로 그렇게 검증한다.
check "상품 목록은 비공개"   GET "/api/v1/products/product-list?page=0&size=1" 401
check "다가오는 drop 비공개" GET "/api/v1/drops/upcoming" 401

echo
echo "=== 인증 흐름 ==="
if [[ -z "$SMOKE_TEST_MEMBER_EMAIL" || -z "$SMOKE_TEST_MEMBER_PASSWORD" ]]; then
    echo "SKIP  [로그인] SMOKE_TEST_MEMBER_EMAIL / SMOKE_TEST_MEMBER_PASSWORD 미설정 — 로그인 테스트 계정을 지정하라"
else
    LOGIN_BODY="$(printf '{"email":"%s","password":"%s"}' "$SMOKE_TEST_MEMBER_EMAIL" "$SMOKE_TEST_MEMBER_PASSWORD")"
    LOGIN_RESPONSE="$(curl -s -X POST "$BASE_URL/api/v1/auth/login" \
        -H 'Content-Type: application/json' -d "$LOGIN_BODY")"
    ACCESS_TOKEN="$(echo "$LOGIN_RESPONSE" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4 || true)"
    if [[ -n "$ACCESS_TOKEN" ]]; then
        echo "PASS  [로그인] access token 발급됨"
        PASS=$((PASS + 1))
        echo
        echo "=== Gateway 신원 header 전달 확인 (인증 필요 endpoint) ==="
        check "인증 필요 주문 목록 조회" GET "/api/v1/orders" 200 -H "Authorization: Bearer $ACCESS_TOKEN"
    else
        echo "FAIL  [로그인] access token을 응답에서 찾지 못함: $LOGIN_RESPONSE"
        FAIL=$((FAIL + 1))
    fi
fi

echo
echo "=== 결제 상태 조회 ==="
echo "결제 상태만 단독 조회하는 공개 API가 없어(현재 코드 기준), 주문 상세 조회 응답의"
echo "orderState로 대신 확인한다(설계와 다르게 작성한 부분). 실제 주문 1건이 필요하다."
if [[ -n "${SMOKE_TEST_ORDER_ID:-}" && -n "${ACCESS_TOKEN:-}" ]]; then
    check "주문 상세(결제 상태 포함) 조회" GET "/api/v1/orders/$SMOKE_TEST_ORDER_ID" 200 \
        -H "Authorization: Bearer $ACCESS_TOKEN"
else
    echo "SKIP  [주문 상세] SMOKE_TEST_ORDER_ID 미설정 — 확인할 실제 주문 ID를 지정하라"
fi

echo
echo "=== 주문 생성(비결제 구간) ==="
echo "주문 생성 요청 본문은 drop/상품 조합에 따라 달라 이 스크립트가 값을 만들지 않는다."
echo "테스트 drop/상품을 골라 아래를 수동으로 1회 확인하라:"
echo "  curl -X POST $BASE_URL/api/v1/orders -H 'Authorization: Bearer <token>' -H 'Content-Type: application/json' -d '{...}'"

echo
echo "=== 요약 ==="
echo "PASS=$PASS FAIL=$FAIL SKIP은 위 로그 참고"
echo "이 결과는 판정이 아니다. Go/No-Go는 사람이 README.md의 체크리스트로 결정한다."
