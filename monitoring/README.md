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

Timer histogram은 실제 `lock-start` 요청이 한 번 이상 실행된 후 보이는지 확인하세요.
Grafana `OpenBake Performance Overview` 하단에는 Phase 5 lock 전용 패널이 추가되어 있습니다.
