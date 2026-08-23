#!/usr/bin/env bash
# 외부 Go 이전 rollback — Compose DB가 여전히 원본이므로 DB는 건드리지 않는다.
# 설계 근거: docs/k3s-learning/12-compose-to-k3s-cutover-rollback-plan.md 11.1장
#
# 전제: Traefik/Ingress를 아직 외부에 열지 않았거나, 열었더라도 k3s가 아직
#       외부 write를 받지 않았다고 확신하는 경우에만 이 스크립트를 쓴다.
#       write를 받았을 가능성이 있으면 이 스크립트를 쓰지 말고 rollback/after-open.md로 간다.
set -Eeuo pipefail

: "${KUBE_NAMESPACE:=openbake}"
: "${COMPOSE_FILE:=docker-compose.yaml:docker-compose.prod.yaml}"

confirm() {
    local prompt="$1"
    local answer
    read -r -p "$prompt (진행하려면 yes 입력): " answer
    [[ "$answer" == "yes" ]] || { echo "중단함: $prompt"; exit 1; }
}

IFS=':' read -r -a _compose_files <<< "$COMPOSE_FILE"
COMPOSE_ARGS=()
for f in "${_compose_files[@]}"; do
    COMPOSE_ARGS+=(--file "$f")
done

echo "=== 확인 ==="
echo "k3s가 외부 write를 받지 않았다고 확신하는가? 확신할 수 없으면 여기서 멈추고"
echo "rollback/after-open.md를 따르라."
confirm "k3s가 외부 write를 받지 않았음을 확신하는가"

echo
echo "=== STEP 1. k3s Ingress 비활성화 ==="
echo "다음 중 하나를 사람이 직접 실행한다 (이 스크립트는 실행하지 않는다):"
echo "  kubectl -n $KUBE_NAMESPACE delete -k k8s/openbake/entrypoint"
echo "  또는: kubectl -n $KUBE_NAMESPACE scale deployment api-gateway --replicas=0"
confirm "Ingress 비활성화(또는 scale down)를 완료했는가"

echo
echo "=== STEP 2. Traefik이 80/443을 내려놓았는지 확인 ==="
echo "Node A에서 확인: sudo ss -ltnp | grep -E ':80|:443'"
confirm "80/443이 Traefik에서 해제된 것을 확인했는가"

echo
echo "=== STEP 3. Compose application·nginx 재가동 ==="
echo "기존 volume을 그대로 쓴다 — Compose DB를 복원하지 않는다."
confirm "docker compose ${COMPOSE_ARGS[*]} up -d 를 지금 실행하겠는가"
docker compose "${COMPOSE_ARGS[@]}" up -d

echo
echo "=== STEP 4. 외부 HTTPS와 핵심 기능 검증 ==="
echo "동일한 확인을 사람이 직접 하거나 ../smoke-test.sh --external EXTERNAL_BASE_URL=<compose 도메인> 로 실행하라."
echo
echo "완료. Compose가 다시 원본이다. root cause를 파악한 뒤 rehearsal부터 다시 진행하라."
