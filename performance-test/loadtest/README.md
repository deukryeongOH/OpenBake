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
| `sample-backend.py` | backend Pod 지표를 **1초 해상도**로 수집 (게이지) |
| `diagnose.py` | NFR 판정 + 원인 진단 + 로그 기록 |
| `warm-measure.sh` | **쓰기 경로까지 예열**하는 측정. 구성 A/B 비교용 |
| `poll-backend.py` | 누적 카운터 수집 (cgroup `cpu.stat` + `/actuator/prometheus`) |
| `poll-report.py` | 부하 구간 자동 탐지 + 구간 차이로 지표 계산 |
| `run-k6-local.sh` | k6만 다른 호스트에서 실행 |

### `run-<N>.sh` vs `warm-measure.sh`

`run-<N>.sh`의 예열은 **`GET /drops/{id}/info` 300회뿐**이다. 정작 재려는
`confirm-entry`·`lock-start`는 한 번도 실행되지 않은 채 k6가 시작하므로,
**측정 구간이 그대로 JIT 컴파일 구간이 된다.** 같은 구성인데 예열 유무로
lock p95가 6.08s와 3.48s로 갈렸다(2026-08-25 실측).

**구성을 바꿔가며 비교할 때는 `warm-measure.sh`를 쓴다.** 드롭 두 개를 만들어
첫 드롭으로 쓰기 경로를 데우고, 재기동 없이 두 번째 드롭에서 잰다.

```bash
export KUBECONFIG=$HOME/.kube/config
kubectl -n openbake port-forward svc/api-gateway 18080:8080 &
CORE_BASE_URL=http://127.0.0.1:18080 ./warm-measure.sh <라벨> 300 300
python3 poll-report.py ../results/loadtest/warm-<라벨>/poll.csv
```

측정 결과와 해석은 `performance-test/LOAD-TEST-REPORT.md`에 있다.
**절대 수치를 인용하기 전에 그 문서의 2절(답할 수 있는 질문/없는 질문)을 읽는다.**

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

## 측정 신뢰도

`diagnose.py`가 매 리포트 하단 `[측정 신뢰도]`에 적는다. 추측이 아니라 매 실행마다 실측한다.

1. **SQL 로깅** — `_common.sh`가 실행마다 실제 Pod에서 확인해 넘긴다.

   ```bash
   kubectl -n openbake exec deployment/backend -c backend -- printenv SPRING_PROFILES_ACTIVE
   kubectl -n openbake logs deployment/backend --tail=500 | grep -c '^Hibernate:'
   ```

   `application-prod.yml`이 `show-sql: false` / `bind: WARN`으로 `application.yml`을
   덮으므로 **prod 프로파일에서는 꺼져 있는 것이 정상**이다. ConfigMap에 오버라이드가
   없다는 사실만으로 "켜져 있다"고 단정하면 오진이다(실제로 그렇게 잘못 적은 적이 있다).

2. **부하 생성기 위치** — 같은 호스트면 CPU 경쟁이 응답시간에 섞인다. 리포트에 기록된다.

3. **P99 측정 여부** — k6 기본 `summaryTrendStats`에 `p(99)`가 없어 그냥 실행하면
   summary에서 빠진다. 빠진 경우 판정은 `PASS`가 아니라 **`UNKNOWN`** 이다.
   `_common.sh`와 `run-sustained.sh`는 `K6_SUMMARY_TREND_STATS`로 강제 포함시킨다.

4. **오토스케일** — `[오토스케일 관측]` 섹션은 판정이 아니라 사실 기록이다.
   테스트가 60초 미만이면 HPA 평가 주기(15초)와 Pod 기동 시간을 못 채우므로,
   `replica 1 → 1`은 "HPA가 반응 안 했다"가 아니라 **"반응할 시간이 없었다"** 로 읽어야
   한다. HPA 자체가 미배포면(`hpa_exists=false`) 그 사실을 따로 적는다.

   > `k8s/openbake/autoscaling/`은 배포 workflow가 apply하지 않는 디렉터리라
   > 현재 클러스터에 HPA가 없다. `k8s/openbake/config/`도 마찬가지다.
   > CI가 배포하는 건 `data/`와 `apps/` 뿐이다.
---

# 지속 부하 테스트 (외부 실행)

`run-<N>.sh`는 버스트(수 초)라 단일 인스턴스 기준선만 준다. 도착률을 유지하며 분 단위로
미는 시나리오는 별도다.

| 파일 | 역할 |
| --- | --- |
| `drop-sustained-load.js` | `ramping-arrival-rate` 지속 부하 시나리오 |
| `run-sustained.sh` | 외부 호스트(노트북)에서 실행하는 러너. kubectl 불필요 |

## 왜 executor가 다른가

기존 시나리오는 `per-vu-iterations` / `ramping-vus` — **closed model**이다. VU는 이전
요청이 끝나야 다음을 보내므로, 서버가 느려지면 **k6가 스스로 요청을 줄인다.** 포화가
지표에 안 드러난다.

`ramping-arrival-rate`는 **open model**이다. 서버 응답 속도와 무관하게 목표 도착률을
유지하려 하고, VU가 모자라면 `dropped_iterations`로 남는다. 실제 사용자는 서버가 느리다고
요청을 늦추지 않으므로 이쪽이 현실에 가깝다.

**핵심 판별식**: 도착률은 올리는데 실제 처리량이 안 오르면 그 지점이 인스턴스 한계다.

## 왜 각 단계를 90초 유지하는가

HPA는 기본 15초마다 평가하고 backend Pod는 `startupProbe` 유예가 240초다. 단계를 30초씩
끊으면 HPA가 판단하기도 전에 다음 단계로 넘어간다. `STEP_HOLD` 기본값 90초는 이 때문이며
오토스케일을 관측하려면 줄이면 안 된다.

기본 계획: `20 → 40 → 60 → 80 → 100 req/s`, 총 약 7.5분.

## 왜 서버 밖에서 돌리는가

노드가 2 vCPU라 서버 안에서 k6를 돌리면 부하 생성기가 측정 대상과 CPU를 다툰다. 실제
측정에서 `http_req_blocked` p95가 전체 p95를 넘겼다 — TLS 핸드셰이크와 커넥션 수립이 CPU
경쟁에 밀렸다는 뜻이고, 그만큼 응답시간이 부풀어 보인다.

---

## 실행 절차

### 1. 노트북에 k6 설치

```powershell
winget install k6 --source winget     # Windows
```
```bash
brew install k6                        # macOS
```

### 2. 대상 확인

Traefik Ingress로 외부에 열려 있다. 포트 개방이나 터널이 필요 없다.

```bash
curl -s https://3.38.24.67.sslip.io/actuator/health
```

### 3. 드롭 준비 (서버)

```bash
cd ~/beadv7_7_BakerySite6_BE/performance-test/prepare
PERF_DROP_STOCK=1000 ./prepare-drop.sh server 100
```

출력의 `dropId`를 적어둔다.

### 4. 계정 준비 (TARGET=confirm일 때만)

`confirm-entry`는 인증이 필요하고 대상 사용자가 **그 드롭에 이미 ENTERED** 여야 한다.

```bash
# 서버
cd ~/beadv7_7_BakerySite6_BE/performance-test
python3 loadtest/create-users.py --count 100 --output users.json
./run-k6.sh server confirm        # 전원 ENTERED 로 만든다
```
```powershell
# 노트북 (레포의 performance-test/ 안으로)
scp ubuntu@3.38.24.67:~/beadv7_7_BakerySite6_BE/performance-test/users.json .
```

> **JWT는 만료된다.** 시간이 지난 뒤 다시 돌릴 거면 `create-users.py`를 재실행해 토큰을
> 새로 받는다(계정은 재사용되고 토큰만 갱신된다).

계정 준비 없이 인프라만 재려면 이 단계를 건너뛰고 `TARGET=info`를 쓴다.

### 5. 서버 지표 수집 시작 (서버, 별도 터미널)

k6는 클라이언트 쪽만 본다. 스레드 풀·CPU·Hikari·replica는 서버에서 따로 받아야 한다.

```bash
cd ~/beadv7_7_BakerySite6_BE/performance-test/loadtest
python3 sample-backend.py --out sustained-sample.csv
```

부하가 끝나면 `Ctrl+C`.

### 6. 부하 실행 (노트북)

```bash
cd performance-test/loadtest
CORE_BASE_URL=https://3.38.24.67.sslip.io DROP_ID=32 ./run-sustained.sh
```

계정 없이:
```bash
CORE_BASE_URL=https://3.38.24.67.sslip.io DROP_ID=32 TARGET=info ./run-sustained.sh
```

Git Bash 없이 PowerShell에서:
```powershell
k6 run `
  -e CORE_BASE_URL=https://3.38.24.67.sslip.io `
  -e DROP_ID=32 -e TARGET=info `
  -e START_RATE=20 -e PEAK_RATE=100 -e STEP_COUNT=5 -e STEP_HOLD=90s `
  --summary-export summary.json `
  drop-sustained-load.js
```

### 7. 판정

```bash
scp ubuntu@3.38.24.67:~/beadv7_7_BakerySite6_BE/performance-test/loadtest/sustained-sample.csv .

python3 diagnose.py \
  --summary ../results/sustained/<run>/summary.json \
  --sample sustained-sample.csv \
  --scenario confirm --users 100 \
  --load-generator-location '노트북 (외부 호스트)'
```

---

## 조정값

| 환경변수 | 기본 | 용도 |
| --- | --- | --- |
| `START_RATE` / `PEAK_RATE` | 20 / 100 | 도착률 범위(req/s) |
| `STEP_COUNT` | 5 | 단계 수 |
| `STEP_HOLD` | 90s | 단계별 유지 시간. **HPA 관측하려면 줄이지 말 것** |
| `MAX_VUS` | 800 | `dropped_iterations`가 나오면 올린다 |
| `SLOW_THRESHOLD_MS` | 1500 | "느리다" 판정 기준 |

## 결과 읽는 법

```
  ---- 무너진 지점 ----
  1500ms 초과 최초 : 60 req/s 구간 (215초)
  오류 최초 발생   : 80 req/s 구간 (338초)

  ---- 처리량 ----
  목표 최대 도착률 : 100 req/s
  실제 평균 처리량 : 62.4 req/s
  미발사(dropped)  : 0건
```

| 관측 | 뜻 |
| --- | --- |
| 도착률 오르는데 처리량 평평 | 포화. **그 지점이 인스턴스 한계** |
| `dropped_iterations` 증가 | `MAX_VUS` 부족. 올리고 재측정 (측정 실패지 서버 문제 아님) |
| 504 / 연결 실패 | 큐 한계 초과 |
| Pod 늘어난 뒤 p99 회복 | 오토스케일이 실제로 먹힘 |

`dropped_iterations`가 0이 아니면 그 실행은 **계획한 부하를 못 밀어넣은 것**이므로 서버
한계로 해석하면 안 된다. `MAX_VUS`를 올려 다시 재야 한다.
