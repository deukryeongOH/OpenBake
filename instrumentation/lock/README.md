# Phase 5 - Drop Lock Contention Instrumentation

## 왜 이 계측이 필요한가

현재 `DropLockFacade`는 Drop ID별 `ReentrantLock`을 `ConcurrentHashMap`에 보관하고,
`tryLock(3, TimeUnit.SECONDS)`로 락 획득을 시도합니다. 기존 코드는 요청마다 `lock wait`을 INFO 로그로만 남겨
Capacity Test 결과와 서버 내부 lock wait/hold 시간을 자동으로 연결하기 어렵습니다.

Phase 5에서는 락 알고리즘을 먼저 바꾸지 않고 **관측 가능성부터 높입니다.**

측정 대상:

- Lock wait P95/P99
- Lock hold P95
- `decreaseQuantity()` P95
- 현재 waiters / holders
- lock timeout / interrupted
- `ConcurrentHashMap` lock 개수

## 적용 방법

프로젝트 루트가 표준 Spring Boot 구조(`src/main/java`)라면:

```bash
cp instrumentation/lock/reference/DropLockMetrics.java \
  src/main/java/com/openbake/drop/application/DropLockMetrics.java

cp instrumentation/lock/reference/DropLockFacade.java \
  src/main/java/com/openbake/drop/application/DropLockFacade.java
```

또는 현재 소스가 Phase 5 기준 소스와 동일하면 patch를 적용할 수 있습니다.

```bash
git apply instrumentation/lock/patches/drop-lock-observability.patch
```

패치 전에 반드시:

```bash
git status
```

로 작업 중 변경사항을 확인하세요.

## 적용 후 검증

```bash
./gradlew build
```

애플리케이션 재시작 후:

```bash
curl -s http://localhost:8080/actuator/prometheus \
  | grep -E 'openbake_drop_lock_(wait|hold|decrease|timeout|interrupted|waiters|holders|map_size)' \
  | head -n 50
```

한 번 이상 `lock-start`를 호출해야 Timer/Counter 계열 metric이 보일 수 있습니다.

`monitoring/.env.monitoring`:

```env
REQUIRE_LOCK_METRICS=true
```

이후:

```bash
cd monitoring
./verify-monitoring.sh
```

## metric 이름

Micrometer의 Prometheus 변환 기준으로 다음 시계열을 사용합니다.

```text
openbake_drop_lock_wait_seconds_bucket{result="acquired|timeout|interrupted"}
openbake_drop_lock_hold_seconds_bucket
openbake_drop_lock_decrease_seconds_bucket
openbake_drop_lock_timeout_total
openbake_drop_lock_interrupted_total
openbake_drop_lock_waiters
openbake_drop_lock_holders
openbake_drop_lock_map_size
```

## 주의

- `dropId`, `memberId`를 Prometheus label로 넣지 않았습니다. ID처럼 계속 증가하는 값은 time-series cardinality를 크게 만들 수 있습니다.
- `lockConcurrentHashMap`의 lock을 여기서 제거하지 않습니다. 단순 `unlock()` 직후 map에서 제거하면 이미 같은 lock을 기다리는 thread와 새로 생성된 lock이 동시에 존재할 위험이 있으므로, cleanup은 별도 동시성 설계가 필요합니다.
- 기존 요청별 `log.info("lock wait...")`는 부하 테스트 자체에 I/O 영향을 줄 수 있어 DEBUG로 낮추고 정량 지표는 Micrometer로 수집하도록 바꿨습니다.
- 이 패치는 성능 개선 패치가 아니라 **원인 분리용 계측 패치**입니다. 실제 Capacity 결과를 확인한 뒤 락 전략 변경 여부를 결정합니다.
