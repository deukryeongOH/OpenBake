# Phase 3 - Lock Capacity Scan

## 목적

현재 `lock-start`는 재고 및 참여 상태를 변경하는 API이므로 같은 사용자가 장시간 반복 호출하는
일반적인 `ramping-vus` 형태를 그대로 적용하면 비즈니스 상태 오류가 섞입니다.

따라서 Phase 3의 첫 단계에서는 **독립적으로 준비된 Drop을 사용자 수별로 나누어 한 번씩 동시 요청**하고,
200 → 250 → 300 → 350 → 400 → 450 → 500 구간에서 P95/P99, 오류, 서버 지표가 급변하는 지점을 찾습니다.

이 단계는 `capacity point 탐색용 concurrent-step scan`입니다. 지속적인 정상 트래픽을 재현하는 Load Test는
별도 Phase에서 반복 가능한 조회/전체 사용자 플로우와 함께 구성합니다.

## 사전조건

각 Capacity step마다 독립 Drop을 준비합니다.

- 대상 사용자들이 `enter` 및 `confirm-entry`를 완료한 상태
- `EXPECTED_SUCCESS` 이상을 처리할 수 있는 재고
- 이전 step의 상태가 다음 step에 영향을 주지 않는 별도 Drop 또는 완전히 초기화된 Drop
- `users.json`은 최대 step 이상의 사용자를 포함
- Prometheus `openbake-core` Target이 UP
- Grafana Dashboard에서 Core 지표 확인 가능

## 실행

```bash
cd performance-test
cp capacity/capacity-plan.example.csv capacity/capacity-plan.csv
```

`capacity-plan.csv`의 `drop_id`를 실제 테스트 Drop으로 변경합니다.

```bash
chmod +x capacity/run-lock-capacity-scan.sh
./capacity/run-lock-capacity-scan.sh
```

결과 집계:

```bash
python3 capacity/analyze-capacity.py
```

생성 파일:

- `capacity/capacity-summary.csv`
- `capacity/capacity-summary.md`

## 판정

현재 Lock NFR:

- P95 < 1500ms
- P99 < 3000ms
- Lock timeout = 0
- Unexpected = 0

사용자 수 증가에 비해 throughput이 더 이상 증가하지 않거나, P95/P99가 급증하거나,
Tomcat/Hikari/JVM 지표가 포화되는 구간을 Capacity Point 후보로 봅니다.

---

# Phase 4 - Observability Correlation

Phase 4에서는 각 k6 run의 `metadata.env`에 기록된 시작/종료 시간을 기준으로 Prometheus 지표를 같이 저장합니다.

Capacity scan은 기본적으로 다음 preflight를 먼저 수행합니다.

```bash
../monitoring/verify-monitoring.sh
```

그 후 각 step마다 다음 파일이 run 폴더에 생성됩니다.

```text
results/runs/<run-id>/
├── console.txt
├── summary.json
├── metadata.env
├── observability.json
└── observability.md
```

수동으로 다시 수집하려면:

```bash
python3 capacity/collect-observability.py --overwrite
```

Prometheus 주소나 job이 다른 경우:

```bash
python3 capacity/collect-observability.py \
  --prometheus-url http://localhost:9090 \
  --job openbake-core \
  --overwrite
```

통합 집계:

```bash
python3 capacity/analyze-capacity.py
```

생성 결과:

```text
capacity/capacity-summary.md
capacity/capacity-repeat-summary.md
```

`bottleneck_candidates`는 원인 확정값이 아닙니다. 자원 포화 여부를 빠르게 분류하기 위한 후보 신호입니다.
P95/P99가 악화되는데 CPU/Tomcat/Hikari/Heap 신호가 없다면 Lock 대기시간이나 DB lock처럼
현재 Actuator metric만으로 보이지 않는 영역을 다음 계측 대상으로 잡습니다.

## 반복성 확인

단일 run은 JVM warm-up, cache, GC, 로컬 머신 상태에 영향을 받을 수 있습니다.
Capacity 경계가 좁혀지면 같은 VU를 서로 다른 독립 Drop으로 최소 3회 정도 반복하고
`capacity-repeat-summary.md`의 median과 min~max를 함께 봅니다.

---

# Phase 5 - Lock Contention Instrumentation

Phase 5에서는 `DropLockFacade`의 ReentrantLock 내부를 Micrometer로 직접 계측합니다.
먼저 루트의 `instrumentation/lock/README.md`에 따라 애플리케이션 패치를 적용하세요.

패치 후 모니터링에서 lock metric을 필수로 검사하려면:

```env
REQUIRE_LOCK_METRICS=true
```

Capacity runner는 Phase 5 기준으로 기본적으로 custom lock metric을 요구합니다.
필요한 경우에만 임시로 다음처럼 우회할 수 있습니다.

```bash
CAPACITY_REQUIRE_LOCK_METRICS=false ./capacity/run-lock-capacity-scan.sh
```

짧은 burst 테스트와 1초 Prometheus scrape를 고려해 관측 수집의 기본 rate window는 `5s`입니다.
변경하려면:

```bash
PROMETHEUS_RATE_WINDOW=10s ./capacity/run-lock-capacity-scan.sh
```

결과 `capacity-summary.md`에는 다음이 추가됩니다.

- Lock wait P95/P99
- Lock hold P95
- decreaseQuantity P95
- waiters/holders
- lock map size
- `wait/P95`, `decrease/hold` 방향성 지표
