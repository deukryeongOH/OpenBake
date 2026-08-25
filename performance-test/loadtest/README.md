# 티어별 부하 테스트 (k3s)

사용자 수를 100 → 300 → 500 → 1000으로 올리며 `confirm-entry`와 `lock-start`를 측정하고,
NFR을 넘겼을 때 원인을 진단해 로그에 남긴다.

| 스크립트 | 사용자 | 재고 | 선점 성공 | 품절 |
| --- | ---: | ---: | ---: | ---: |
| `run-100.sh` | 100 | 200 | 100 | 0 |
| `run-300.sh` | 300 | 200 | 200 | 100 |
| `run-500.sh` | 500 | 300 | 300 | 200 |
| `run-1000.sh` | 1000 | 700 | 700 | 300 |

**NFR: P95 ≤ 1500ms, P99 ≤ 3000ms.** 넘기면 종료 코드 1과 함께 진단이 로그에 쌓인다.

---

## 파일 구성

| 파일 | 역할 |
| --- | --- |
| `run-<N>.sh` | 티어별 진입점. 사용자 수와 재고만 정하고 `run_tier` 호출 |
| `_common.sh` | 실제 절차. 계정 → 드롭 → 롤아웃 → 예열 → confirm → lock → 판정 |
| `create-users.py` | 계정 생성 + 로그인 → `users-<N>.json` |
| `sample-backend.py` | backend Pod 지표를 **1초 해상도**로 수집 |
| `diagnose.py` | NFR 판정 + 원인 진단 + 로그 기록 |

절차를 `_common.sh` 한 곳에 모은 이유는, 티어별로 복사하면 한쪽만 수정되는 사고가 나기 때문이다.
티어 파일은 값만 다르다.

---

## 사전 준비

### 1. 실행 위치

**서버(EC2)에서 실행한다.** 드롭 생성이 `kubectl exec`로 DB에 직접 접근하기 때문이다
(`perf-data.py`의 `db_scalar` — 드롭 슬롯 조회·dropId 조회·즉시 활성화).

> k6도 같은 호스트에서 돌게 된다. 부하 생성기가 측정 대상과 CPU를 나눠 쓰므로
> 사용자 수가 큰 티어(500·1000)에서는 이 영향이 커진다. `diagnose.py`가
> `http_req_waiting` 비중을 보고 "서버 밖 구간이 지연을 지배"로 잡아준다.
> 그 항목이 `[X]`로 뜨면 부하 생성기를 별도 호스트로 옮겨 재측정해야 한다.

### 2. `prepare/.env.server`

```env
SERVER_BASE_URL=https://3.38.24.67.sslip.io
AUTH_MODE=gateway

# k3s
PERF_EXEC_MODE=kubectl
PERF_K8S_NAMESPACE=openbake
PERF_DB_WORKLOAD=statefulset/core-postgres
PERF_BACKEND_WORKLOAD=deployment/backend

PERF_DB_ENABLED=true
```

`PERF_EXEC_MODE=kubectl`이 없으면 `docker exec openbake-postgres`를 시도해서 실패한다.

### 3. `prepare/.env.perf`

재고는 스크립트가 `PERF_DROP_STOCK`으로 주입하므로 건드리지 않아도 된다.
`PERF_RESTART_BACKEND`도 스크립트가 `false`로 덮어쓴다 — 롤아웃은 `_common.sh`가
직접 하고 완료까지 기다린다.

> `perf-data.py`의 rollout status 타임아웃이 120초로 고정돼 있는데, backend
> `startupProbe`는 `failureThreshold: 48 × period 5s = 240초`까지 허용한다.
> 그대로 쓰면 정상 기동 중에 실패로 끊긴다. 그래서 `_common.sh`가 420초로 기다린다.

### 4. 판매자 fixture (최초 1회)

```bash
cd performance-test/prepare
PERF_EXEC_MODE=kubectl ./setup-seller.sh server
```

### 5. kubectl

```bash
export KUBECONFIG=$HOME/.kube/config
kubectl -n openbake get deploy backend hpa backend
```

`sudo k3s kubectl`이 필요하면 `PERF_KUBECTL="sudo k3s kubectl"`로 넘긴다.

---

## 실행

```bash
cd performance-test/loadtest
./run-100.sh
```

티어 하나에 **10분 이상** 걸린다. 롤아웃(최대 4분) + JVM 예열(2분) + 계정 생성 + 두 시나리오.
1000명 티어는 계정 생성만으로도 시간이 꽤 든다.

```bash
./run-100.sh && ./run-300.sh && ./run-500.sh && ./run-1000.sh
```

### 자주 쓰는 조정값

| 환경변수 | 기본 | 용도 |
| --- | --- | --- |
| `PERF_WARMUP_SECONDS` | 120 | 롤아웃 후 예열 대기. 0이면 생략(측정값 나빠짐) |
| `PERF_WARMUP_REQUESTS` | 300 | 조회 경로 예열 횟수 |
| `REUSE_USERS` | true | `users-<N>.json` 재사용 (토큰은 항상 새로 받음) |
| `SAMPLE_INTERVAL` | 1 | 지표 샘플링 간격(초) |
| `ACCOUNT_WORKERS` | 8 | 계정 생성 동시성. 높이면 member-service가 죽는다 |
| `K6_INSECURE_SKIP_TLS_VERIFY` | – | 인증서 검증 실패 시 `true` |

---

## 측정 항목

### 요청하신 7개

| 항목 | 출처 |
| --- | --- |
| 스레드 풀 | `tomcat_threads_busy_threads` / `config_max_threads`, busy% |
| CPU | `process_cpu_usage`(Pod, 1.0 = limit 1000m), `system_cpu_usage`(노드) |
| 초당 요청 수 | k6 `http_reqs` rate + Pod actuator 기준 실측 |
| P95 / P99 | 시나리오별 business duration (`confirm_entry_*`, `drop_lock_*`) |
| 초당 처리량 | k6 `iterations` rate |
| 성공 / 실패 | 시나리오 카운터 + 기대값 대조 |

### 추가한 항목

| 항목 | 왜 필요한가 |
| --- | --- |
| **Hikari active / pending / max** | 스레드가 CPU를 쓰는지 DB 커넥션을 기다리는지 구분. pending > 0이면 커넥션 부족 |
| **힙 사용률 + GC pause/sec** | `-Xmx512m`라 여유가 적다. GC가 시간의 10%를 넘으면 그만큼 응답시간에 실린다 |
| **replica 수 + HPA 현재/목표** | 오토스케일이 실제로 발동했는지. max 2 도달 여부 |
| **노드 CPU** | backend는 `nodeAffinity: required`로 node-a 고정. 이웃 Pod와의 경쟁을 분리 |
| **응답시간 분해** | `http_req_waiting` 비중으로 서버 내부 vs 네트워크/부하생성기 구분 |
| **동시 처리량(Little's law)** | `처리량 × 평균지연`을 Tomcat max와 비교. 스레드 상한 근접 여부 |
| **정합성 대조** | 성공/품절 개수가 재고와 맞는지. 성능과 별개 축 |

### 왜 Prometheus를 안 쓰는가

`k8s/monitoring/prometheus/configmap.yaml`의 `scrape_interval`이 **15초**다.
`confirm`/`lock`은 `per-vu-iterations` 버스트라 수 초에 끝나므로 표본이 0~1개만 남는다.
그 표본으로는 "스레드 풀이 포화됐다"를 말할 수 없다.

그래서 `sample-backend.py`가 `kubectl port-forward`로 각 Pod의
`/actuator/prometheus`를 1초마다 직접 긁는다. `port-forward`는 API server → kubelet
경로라 `default-deny` NetworkPolicy와 무관하게 붙는다. HPA로 Pod가 늘면
10초마다 재탐색해 새 Pod에도 붙는다.

Grafana 시계열은 여전히 유효하다. 다만 15초 해상도라는 것만 기억할 것.

---

## 결과물

```
performance-test/results/loadtest/
├── nfr-log.md                                   ← 모든 실행의 판정이 누적된다
└── 20260824T...-lock-u1000-drop42/
    ├── console.txt      k6 원본 출력
    ├── summary.json     k6 지표
    ├── sample.csv       1초 해상도 서버 지표
    ├── metadata.env     사용자/재고/dropId
    └── diagnosis.txt    판정 + 원인 진단
```

`nfr-log.md`가 리포트의 원본이 된다.

---

## 알려진 측정 왜곡

`diagnose.py`가 매 리포트 하단에 이 두 개를 항상 적는다.

1. **SQL 로그가 켜져 있다.** `backend-config.yaml`에 `SPRING_JPA_SHOW_SQL` 오버라이드가
   없어 `application.yml`의 `show-sql: true`, `hibernate.orm.jdbc.bind: trace`가 그대로
   적용된다. 모든 요청이 쿼리와 바인딩 파라미터를 stdout에 쓰고, 그 과정에서 스레드가
   콘솔 락에 직렬화된다. 끄려면 ConfigMap에 추가한다.

   ```yaml
   SPRING_JPA_SHOW_SQL: "false"
   SPRING_JPA_PROPERTIES_HIBERNATE_FORMAT_SQL: "false"
   LOGGING_LEVEL_ORG_HIBERNATE_ORM_JDBC_BIND: "warn"
   ```

2. **버스트가 짧아 HPA는 반응하지 못한다.** metrics-server 수집 주기 + `scaleUp`
   30초당 1개 + `startupProbe` 최대 240초가 필요하다. 이 시나리오로 얻는 것은
   **단일 인스턴스 기준선**이고, 오토스케일 효과는 분 단위로 지속되는 시나리오가
   따로 있어야 측정된다.