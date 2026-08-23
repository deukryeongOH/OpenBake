# k8s/

OpenBake의 k3s 매니페스트 저장소. 관리 방식은 Kustomize-light (`kubectl apply -k`)이며, base/overlay 분리는 두 번째 환경이 생기기 전까지 도입하지 않는다.

설계 근거 문서:

- `docs/k3s-learning/10-kubernetes-manifest-structure-and-apply-order.md` — 구조·이름 규칙·적용 순서
- `docs/k3s-learning/06-namespace-label-networkpolicy-design.md` — Namespace, label, NetworkPolicy
- `docs/k3s-learning/08-configmap-secret-configuration-design.md` — ConfigMap/Secret 분류

## 디렉터리

```text
k8s/
├── cluster/          Namespace, StorageClass, Traefik(HelmChartConfig — cutover 전까지 비활성)
├── secrets/           실제 값 없는 Secret 생성 안내(README, *.env.example)
└── openbake/
    ├── config/        ConfigMap (서비스별)
    ├── network/        NetworkPolicy (allow, default-deny)
    ├── data/           PostgreSQL 4종·Redis·Elasticsearch·Kafka StatefulSet
    ├── apps/           backend·member-service·payment-service·api-gateway·ai-service Deployment
    ├── jobs/           PostgreSQL logical backup CronJob 4종
    ├── entrypoint/      Ingress (api-gateway 5개 경로, TLS — cutover 때만 적용)
    └── autoscaling/     backend HPA(autoscaling/v2, min1/max2, CPU 65%)

k8s/monitoring/
├── prometheus/        StatefulSet + Kubernetes Pod Discovery, Node B 고정, 3Gi PVC
└── grafana/           Deployment(Recreate) + 기존 대시보드 재사용, Node B 고정, 1Gi PVC
```

Loki·log-agent는 아직 없다(03번 문서에 자원 예약값만 있고 설정 설계가 없어 이슈 4 범위에서 제외). blackbox_exporter도 아직 없다(`scripts/infra/node-a-alerting/README.md` 참고).

## 현재 상태 (이슈 4)

- `k8s/monitoring/`이 새로 생겼다. Prometheus는 15초 scrape·Kubernetes Pod Discovery로 다섯 서비스를 자동 수집하고, Grafana는 `monitoring/grafana/dashboards/`의 기존 대시보드를 복사해 재사용한다(Kustomize가 kustomization root 밖 파일을 참조하지 못해 복사본을 둔다 — 원본과 별도 관리).
- `k8s/openbake/autoscaling/backend-hpa.yaml`이 채워졌고, `apps/backend/deployment.yaml`의 `replicas: 1` 고정값을 제거했다(HPA와의 apply 충돌 방지).
- `k8s/openbake/network/allow/allow-prometheus-to-ai-service.yaml`을 추가했다(다섯 서비스 중 ai-service scrape 허용이 빠져 있었다).

## 현재 상태 (이슈 3 PR1·PR2·PR3)

- `data/`, `jobs/`은 이슈 3 PR1(데이터 계층 + 백업)로 채워졌다.
- `apps/`는 이슈 3 PR2(애플리케이션 워크로드)로 채워졌다.
- `apps/*/deployment.yaml`의 image tag는 `sha-placeholder`다. 10번 문서 10장에 따라 실제 배포 시 임시 디렉터리에서 `kustomize edit set image`로 Git SHA를 주입한다. 이 repository에 실제 SHA를 영구 반영하지 않는다.
- `k8s/openbake/jobs/postgres-backup/configmap.yaml`의 `BACKUP_S3_BUCKET`, `AWS_REGION`은 실제 값 미확정 placeholder다. 적용 전 실제 값으로 교체해야 한다.
- `cluster/traefik/`, `openbake/entrypoint/`는 이슈 3 PR3(Traefik + 외부 진입점)으로 채워졌다. ACME email `hhh3915@gmail.com`, 외부 도메인 `3.38.24.67.sslip.io`(Node A Elastic IP)로 확정.
  - **이 둘은 cutover(이슈 5) 전까지 실제로 동작하지 않는다.** 아래 "외부 진입점은 cutover 전용" 참고.

## 외부 진입점은 cutover 전용

Node A의 `80/443`은 아직 Compose nginx가 점유하고 있다(`docker-compose.yaml`의 `nginx` 서비스). 충돌을 막기 위해 k3s는 Traefik과 ServiceLB를 **끈 상태로 설치**했다.

```yaml
# Node A: /etc/rancher/k3s/config.yaml (14번 문서 5~6장)
disable:
  - traefik
  - servicelb
```

그 결과 다음이 성립한다.

- `k8s/cluster`를 지금 적용해도 **Traefik HelmChartConfig는 아무 동작도 하지 않는다.** 대상 HelmChart가 없기 때문이다. Namespace와 StorageClass는 정상 적용되므로 순서상 먼저 적용해야 한다.
- `k8s/openbake/entrypoint`(Ingress)는 **cutover 전에 적용하지 않는다.** 12번 문서 3.2장: "전환 전에는 k3s Ingress를 외부에 열지 않는다."
- cutover 전 애플리케이션 검증은 `kubectl port-forward` 또는 cluster 내부 임시 Pod로 한다.
- **TLS 인증서 발급, HTTP→HTTPS redirect, 외부 경로 차단 검증은 이슈 3이 아니라 이슈 5에서 수행한다.** 12번 문서의 전환 시간표에 `nginx→Traefik·TLS 전환 10~20분`으로 잡혀 있다.

### cutover 당일 순서

포트 소유권 전환은 한 시점에 통제한다.

```bash
# 1. Compose nginx 중지 — 80/443 해제
docker compose stop nginx certbot

# 2. Node A에서 Traefik·ServiceLB 활성화 (config.yaml의 disable 항목 제거 후)
sudo systemctl restart k3s

# 3. ServiceLB를 Node A로 제한 (매니페스트로 표현되지 않는 Node label 작업)
kubectl label node openbake-node-a svccontroller.k3s.cattle.io/enablelb=true

# 4. Traefik 설정 반영 — 이 시점에 HelmChartConfig가 실제로 적용된다
kubectl apply -k k8s/cluster

# 5. Ingress 적용과 인증서 발급 확인
kubectl apply -k k8s/openbake/entrypoint
```

사전 확인:

- Node A Security Group에 인터넷 → TCP **80** 인바운드가 열려 있어야 한다. ACME HTTP-01(`httpchallenge.entrypoint=web`)이 80번을 사용한다.
- 최초 발급은 `k8s/cluster/traefik/helm-chart-config.yaml`의 staging CA 주석을 잠깐 활성화해 rate limit 없이 흐름을 검증한 뒤, 반드시 제거하고 production으로 재배포한다.
- `k8s/secrets/`에는 실제 Secret 값이 없다. 생성 절차는 `k8s/secrets/README.md` 참고.
- PostgreSQL 백업/복구 절차와 k3s SQLite datastore 백업은 `docs/k3s-learning/17-postgres-backup-restore-and-k3s-sqlite-backup.md` 참고.

## 적용 순서

전체 순서는 `10-kubernetes-manifest-structure-and-apply-order.md` 14장 참고.
각 단계마다 `kubectl apply --dry-run=server -k <dir>` → `kubectl diff -k <dir>` → `apply` 순으로 진행한다.

### cutover 전 (기존 Compose는 계속 운영 중)

```bash
# Namespace, StorageClass. Traefik HelmChartConfig는 함께 적용되지만 비활성 상태로 남는다.
kubectl apply -k k8s/cluster

# 실제 Secret은 k8s/secrets/README.md 절차로 별도 생성
kubectl apply -k k8s/openbake/config
kubectl apply -k k8s/openbake/network/allow
kubectl apply -k k8s/openbake/network/default-deny

# 이슈 3 PR1 — 데이터 계층부터 Ready 확인 후 진행
kubectl apply -k k8s/openbake/data

# 이슈 3 PR2 — member/payment → backend → api-gateway 순서로 Ready 확인
kubectl apply -k k8s/openbake/apps

# 이슈 3 PR1 — backup CronJob (data 적용 후, 수동 1회 실행으로 검증)
kubectl apply -k k8s/openbake/jobs

# 이슈 4 — Prometheus/Grafana. metrics-server, backend Ready 확인 후 진행
kubectl apply -k k8s/monitoring

# 이슈 4 — HPA. Metrics Server 응답과 backend CPU request 확인 후 마지막에 적용
kubectl apply -k k8s/openbake/autoscaling
```

여기까지는 외부 트래픽을 받지 않는다. 검증은 `kubectl port-forward`로 한다.

### cutover 때 (이슈 5)

`k8s/openbake/entrypoint`와 Traefik 활성화는 위 "외부 진입점은 cutover 전용"의 순서를 따른다.
