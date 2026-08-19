# OpenBake Monitoring - Phase 2

## 목표

k6 결과만 보는 단계에서 벗어나, 같은 시간대의 Spring Boot/JVM/Connection Pool 지표를 함께 확인합니다.

```text
k6
 │ HTTP
 ▼
OpenBake Spring Boot
 │
 └─ /actuator/prometheus
        │
        ▼
   Prometheus :9090
        │
        ▼
    Grafana :3001
```

## 1. 사전 조건

각 서비스에서 다음 URL이 열려 있어야 합니다.

```text
Core    http://<host>:8080/actuator/prometheus
Member  http://<host>:8081/actuator/prometheus
Payment http://<host>:8082/actuator/prometheus
```

HTTP P95/P99 패널은 `http.server.requests` histogram/bucket이 노출될 때 동작합니다.

## 2. 환경파일

```bash
cd monitoring
cp .env.monitoring.example .env.monitoring
```

반드시 Grafana 비밀번호를 변경합니다.

```env
GRAFANA_ADMIN_PASSWORD=실제_비밀번호
```

`.env.monitoring`은 Git에 커밋하지 않습니다.

## 3. 실행

```bash
chmod +x run-monitoring.sh verify-monitoring.sh
./run-monitoring.sh
```

확인:

```bash
./verify-monitoring.sh
```

## 4. 접속

```text
Prometheus http://localhost:9090
Grafana    http://localhost:3001
```

Grafana에는 시작 시 자동으로 다음이 생성됩니다.

```text
Data source: Prometheus
Folder     : OpenBake
Dashboard  : OpenBake Performance Overview
```

## 5. Dashboard 주요 지표

### k6 (Remote Write를 켰을 때)

- VUs
- Requests/sec
- HTTP failed rate
- HTTP request P95/P99

### Spring / JVM

- HTTP Requests/sec
- HTTP P95/P99
- HTTP 5xx rate
- Process CPU
- JVM Heap used
- GC pause count/sec
- Tomcat busy/current/max threads
- Hikari active/idle/pending/max connections

## 6. k6 -> Prometheus Remote Write (선택)

`.env.k6`에서 아래를 설정합니다.

```env
K6_PROMETHEUS_RW_ENABLED=true
K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write
K6_PROMETHEUS_RW_TREND_STATS='p(95),p(99),min,max'
K6_PROMETHEUS_RW_PUSH_INTERVAL=1s
```

이 기능을 켜면 `run-k6.sh`가 실행별 `testid` 태그를 붙여 Prometheus로 k6 시계열을 보냅니다.

기본값은 `false`입니다. k6 Prometheus Remote Write output이 experimental 모듈이므로, 콘솔/summary.json은 계속 기준 결과로 보존합니다.

## 7. 해석 원칙

예를 들어 P95가 급증할 때 같은 시간대에 다음을 같이 봅니다.

```text
P95 ↑
  ├─ CPU ↑ ?
  ├─ JVM Heap / GC ↑ ?
  ├─ Tomcat busy threads ↑ ?
  └─ Hikari pending ↑ ?
```

한 지표만 보고 원인을 확정하지 않습니다. 이 단계의 목적은 Capacity Test 전에 관측 가능성을 확보하는 것입니다.

## 8. 주의

`scrape_interval: 1s`는 짧은 동시성 테스트의 순간 변화를 보기 위한 성능 테스트 환경 설정입니다. 운영 환경 설정으로 그대로 복사하지 않습니다.

## 9. Phase 4 - Metric availability 확인

`verify-monitoring.sh`는 Target UP뿐 아니라 병목 분석에 필요한 metric이 실제로 Prometheus에 존재하는지도 확인합니다.

```bash
./verify-monitoring.sh
```

별도로 확인하려면:

```bash
python3 verify-metrics.py \
  --prometheus-url http://localhost:9090 \
  --job openbake-core
```

Process CPU, JVM Heap, HTTP Server는 필수 확인 대상으로 처리합니다.
Tomcat/Hikari/HTTP histogram은 애플리케이션 설정 및 실제 사용 여부에 따라 WARN이 날 수 있습니다.

## Phase 5 - Drop Lock custom metrics

`instrumentation/lock` 패치를 Core에 적용한 후 `.env.monitoring`에 다음을 설정할 수 있습니다.

```env
REQUIRE_LOCK_METRICS=true
```

그 다음:

```bash
./verify-monitoring.sh
```

에서 다음 metric까지 확인합니다.

```text
Drop Lock Wait Histogram
Drop Lock Hold Histogram
Drop Lock decreaseQuantity Histogram
Drop Lock Waiters
Drop Lock Holders
Drop Lock Map Size
```

재고 선점 테스트에서는 HTTP/Tomcat/Hikari/JVM 지표를 함께 확인하세요.
Grafana `OpenBake Performance Overview` 하단에는 Phase 5 lock 전용 패널이 추가되어 있습니다.


# OpenBake Monitoring - Local / Server 공용 개선본

## 문제 원인

기존 `run-monitoring.sh`는 일반 Linux 서버에서 `host.docker.internal`을 기본 target으로 사용합니다.

하지만 서버의 Core/Member/Payment는 다음처럼 Docker 내부 포트만 열려 있습니다.

- `openbake-backend` -> `8080/tcp`
- `openbake-member-service` -> `8081/tcp`
- `openbake-payment` -> `8082/tcp`

호스트에 `8080/8081/8082`가 publish되지 않았기 때문에 서버의 Prometheus 컨테이너에서
`host.docker.internal:8080`으로 접근하면 connection refused가 발생합니다.

## 개선 내용

`run-monitoring.sh`가 local/server를 구분합니다.

### auto 모드 (기본/권장)

```bash
./run-monitoring.sh
```

판정 순서:

1. `localhost:8080`에 Core가 있으면 `local`
2. 그렇지 않고 `openbake-backend` 컨테이너가 실행 중이면 `server`

따라서 기존 로컬에서는 별도 인자 없이 계속 사용할 수 있고,
현재 Docker 서버에서도 별도 인자 없이 자동으로 server 모드가 선택됩니다.

명시 실행도 가능합니다.

```bash
./run-monitoring.sh local
./run-monitoring.sh server
```

## local 모드

기존 로컬 동작을 보존합니다.

- WSL이면 WSL IPv4 자동 탐지
- Core 8080
- Member 8081
- Payment 8082

Prometheus는 호스트에서 bootRun 중인 애플리케이션을 scrape합니다.

## server 모드

Prometheus target:

- `openbake-backend:8080`
- `openbake-member-service:8081`
- `openbake-payment:8082`

그리고 `openbake-backend`의 실제 Docker network를 자동으로 찾은 후
`openbake-prometheus`를 그 network에 추가 연결합니다.

애플리케이션의 8080/8081/8082 포트를 외부에 publish할 필요가 없습니다.

## 적용 파일

- `monitoring/run-monitoring.sh`
- `monitoring/verify-monitoring.sh`
- `monitoring/.env.monitoring.example`
- `monitoring/prometheus/prometheus.yml.template`

기존 `.env.monitoring`의 비밀번호/환경값은 덮어쓰지 않는 것을 권장합니다.

기존 `.env.monitoring`을 유지해도 새 스크립트는
`LOCAL_*`, `SERVER_*`, `CORE_CONTAINER` 등의 값을 지원합니다.

## 실행

```bash
cd monitoring

chmod +x run-monitoring.sh verify-monitoring.sh

./run-monitoring.sh
./verify-monitoring.sh
```

서버에서 정상이라면 다음과 같이 보여야 합니다.

```text
Profile     : server
Core        : openbake-backend:8080
Member      : openbake-member-service:8081
Payment     : openbake-payment:8082
```

그리고 검증 결과:

```text
OK: openbake-core = UP
OK: openbake-member = UP
OK: openbake-payment = UP
```
