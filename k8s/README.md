# k8s/

OpenBake의 k3s 매니페스트 저장소. 관리 방식은 Kustomize-light (`kubectl apply -k`)이며, base/overlay 분리는 두 번째 환경이 생기기 전까지 도입하지 않는다.

설계 근거 문서:

- `docs/k3s-learning/10-kubernetes-manifest-structure-and-apply-order.md` — 구조·이름 규칙·적용 순서
- `docs/k3s-learning/06-namespace-label-networkpolicy-design.md` — Namespace, label, NetworkPolicy
- `docs/k3s-learning/08-configmap-secret-configuration-design.md` — ConfigMap/Secret 분류

## 디렉터리

```text
k8s/
├── cluster/          Namespace, StorageClass, Traefik(추후)
├── secrets/           실제 값 없는 Secret 생성 안내(README, *.env.example)
└── openbake/
    ├── config/        ConfigMap (서비스별)
    ├── network/        NetworkPolicy (allow, default-deny)
    ├── data/           이슈 3 — PostgreSQL·Redis·Elasticsearch·Kafka
    ├── apps/           이슈 3 — Deployment
    ├── jobs/           이슈 3 — CronJob
    ├── entrypoint/      이슈 3 — Ingress
    └── autoscaling/     이슈 3 — HPA
```

`data/`, `apps/`, `jobs/`, `entrypoint/`, `autoscaling/`은 이슈 3에서 채운다. AI·Kafka가 아직 워크로드로 존재하지 않으므로 빈 디렉터리를 미리 만들지 않는다.

## 현재 상태 (이슈 2)

- `cluster/traefik/`은 아직 없다. Traefik이 `--disable traefik`으로 비활성 상태라 chart version을 확인할 수 없고, ACME 등록 email도 미확정이다. cutover 직전에 추가한다.
- `k8s/secrets/`에는 실제 Secret 값이 없다. 생성 절차는 `k8s/secrets/README.md` 참고.

## 적용 순서

전체 순서는 `10-kubernetes-manifest-structure-and-apply-order.md` 14장 참고. 이슈 2 범위(cluster, config, network)만 적용하려면:

```bash
kubectl apply -k k8s/cluster
# 실제 Secret은 k8s/secrets/README.md 절차로 별도 생성
kubectl apply -k k8s/openbake/config
kubectl apply -k k8s/openbake/network/allow
kubectl apply -k k8s/openbake/network/default-deny
```
