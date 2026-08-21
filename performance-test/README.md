# OpenBake 부하 테스트 — 서버 실행 런북

이 문서가 답하려는 질문은 둘이다.

1. **초과 판매가 발생하는가?** (정합성)
2. **Drop을 다중 인스턴스로 전환해야 하는가?** (용량)

두 질문은 성격이 달라서 보는 지표도 다르다. 아래에 각각의 실행 절차와 판정 기준을 적는다.

---

## 0. 사전 확인

### 서버에 어떤 코드가 떠 있는지부터 확인한다

배포는 `develop` 브랜치 push에서만 돈다(`.github/workflows/deploy.yml`). 작업 중인 브랜치의 변경은
머지·배포 전까지 서버에 반영되지 않는다. **측정 대상이 어떤 코드인지 모르면 결과를 해석할 수 없다.**

```bash
curl -s https://<server>/actuator/info
docker exec openbake-backend printenv SPRING_PROFILES_ACTIVE
```

### 측정을 왜곡하는 설정

| 항목 | 확인 | 영향 |
| --- | --- | --- |
| `spring.jpa.show-sql` | `true`면 끄고 재측정 | 모든 톰캣 스레드가 `System.out` 락에서 직렬화된다. 앱 CPU가 포화된 것처럼 보여 **다중 인스턴스 판단이 왜곡된다** |
| Hikari `maximum-pool-size` | 미설정 시 기본 10 | 톰캣 스레드 200과 불균형. 커넥션 대기가 병목이면 스케일아웃이 답이 아니다 |
| k6 실행 위치 | 서버 안에서 돌리면 부하 생성기가 CPU를 같이 먹는다 | 별도 장비에서 실행 권장 |

### 환경 파일

```bash
cd performance-test
cp .env.k6.server.example .env.k6.server
```

서버는 Gateway가 JWT를 검증하므로 `AUTH_MODE=gateway`여야 한다. `CORE_BASE_URL`은 Gateway 주소를 가리킨다.

---

## 1. 초과 판매 검증

### 원리

재고보다 **많은** 사용자를 동시에 붙인다. 재고 30에 사용자 100명이면 정확히 30명만 성공해야 한다.

k6의 성공 카운트만 세는 것으로는 부족하다. 서버가 실제로 몇 개를 팔았는지 대조해야 한다.
그래서 `drop-oversell-verification.js`는 `GET /api/v1/drops/{id}/info`로 **시작 재고와 종료 재고를 직접 읽어**
감소분과 성공 응답 수를 맞춰본다.

### 준비

```bash
cd performance-test/prepare
./setup-seller.sh server
./prepare-drop.sh server 100          # 사용자 100명 기준 Drop 생성 + ACTIVE 처리
```

`.env.k6.server`에서 재고를 사용자 수보다 **적게** 맞춘다.

```env
DROP_ID=<생성된 drop id>
USER_COUNT=100
QUANTITY=1
```

재고는 `prepare/.env.perf`의 `PERF_DROP_STOCK`으로 조절한다. 예: 재고 30 / 사용자 100.

### 실행

```bash
cd performance-test
./run-k6.sh server users        # users.json 생성 (JWT 포함)
./run-k6.sh server confirm      # 전원 입장 확정 — lock-start는 ENTERED 상태를 요구한다
./run-k6.sh server oversell     # 초과 판매 검증
```

> `confirm`을 건너뛰면 전원이 `DR014`(ENTERED 상태 아님)로 떨어져 재고를 건드리지 못한다.

### 판정 기준

요약 블록이 네 줄로 답한다.

```
✅ PASS  재고 초과 판매 없음        (초과분 0)
✅ PASS  잔여 재고 음수 아님        (0)
✅ PASS  서버·클라이언트 수량 일치  (서버 30 / 응답 30)
✅ PASS  예상밖 오류 없음
```

| 지표 | 정상 | 이상이면 |
| --- | --- | --- |
| `oversell_units` | `0` | **초과 판매 발생.** Redis 원자 차감 또는 `WHERE entryStatus='ENTERED'` 가드가 뚫린 것 |
| `stock_remain_after` | `>= 0` | 음수 재고. 위와 같은 뜻 |
| 서버 판매량 vs 성공 응답 수 | 일치 | 불일치는 커밋 직전 Redis만 차감된 케이스. **적게 표시되는 방향이면** 초과 판매가 아니다 |
| `lock_stock_not_init` (DR022) | `0` | Redis 카운터 유실. 워밍업이 안 돌았거나 Redis가 재시작됐다 |
| `lock_duplicate_request` (C004) | `0` | 같은 사용자의 동시 요청 충돌. 시나리오상 1인 1요청이므로 나오면 안 된다 |

**DR007(품절)은 정상 결과다.** 재고를 못 얻은 사용자가 받는 응답이므로 threshold에서 제외했다.

---

## 2. 다중 인스턴스 전환 판단

두 가지를 나눠서 봐야 한다. **가능한가(정합성)** 와 **필요한가(용량)** 는 다른 질문이다.

### 2-1. 가능한가 — 코드 점검 (부하 테스트 아님)

인스턴스 로컬 상태가 남아 있으면 인스턴스를 늘리는 순간 깨진다.

| 항목 | 상태 | 근거 |
| --- | --- | --- |
| 인메모리 대기열 | ✅ 제거됨 | `07` 문서 |
| 드롭 상태 전환 1회 보장 | ✅ 조건부 UPDATE | `CachedDrop`의 `AtomicBoolean`이 아니라 `WHERE` 절이 보장 |
| `TodayDropCache` | ✅ 안전 | 읽기 전용 파생값. 인스턴스별 사본이 같은 DB에서 나온다 |
| 재고 카운터 | ✅ 안전 | Redis 원자 연산 |
| 중복 참여 차단 | ✅ 안전 | `uk_drop_member` + `WHERE entryStatus='ENTERED'` |
| `DropLockFacade`의 `ReentrantLock` | ⚠️ 미사용 | 컨트롤러가 `DropLockService`를 직접 호출한다. 되살린다면 분산락으로 교체 필요 |

즉 **현재 코드는 다중 인스턴스가 가능한 상태**다. 부하 테스트로 확인할 것은 "필요한가"뿐이다.

### 2-2. 필요한가 — Capacity Scan

```bash
cd performance-test
cp capacity/capacity-plan.example.csv capacity/capacity-plan.csv
# capacity-plan.csv의 drop_id를 step별 독립 Drop으로 채운다
./capacity/run-lock-capacity-scan.sh
python3 capacity/collect-observability.py     # Prometheus에서 서버 지표 수집
python3 capacity/analyze-capacity.py
```

200 → 250 → 300 → 350 → 400 → 450 → 500 구간에서 꺾이는 지점을 찾는다.

### 판정 기준 — 병목이 어디냐에 따라 답이 갈린다

처리량이 평평해지는 지점에서 **무엇이 포화됐는지**를 봐야 한다. 이걸 안 보면
"느리니까 인스턴스 늘리자"는 잘못된 결론이 나온다.

| 포화된 자원 | 관측 지표 | 스케일아웃 효과 | 먼저 할 일 |
| --- | --- | --- | --- |
| **앱 CPU** | `process_cpu_percent` 80%+, `tomcat_busy_percent` 높음 | ✅ **효과 있음** | 인스턴스 증설 |
| **DB 커넥션 풀** | `hikari_pending` > 0, `hikari_active_percent` 100% | ⚠️ 부분적 | **풀 크기 먼저 조정.** 인스턴스를 늘리면 DB 커넥션 총량만 늘어 DB가 더 힘들어진다 |
| **Redis 단일 스레드** | 앱 CPU 낮은데 `lock` P95만 증가 | ❌ **효과 없음** | 직렬화 지점이라 인스턴스를 늘려도 그대로다. 샤딩이나 설계 변경이 필요 |
| **로그 I/O** | `show-sql=true`, CPU는 낮은데 P95 높음 | ❌ 효과 없음 | `show-sql` 끄기 |
| **DB row 경합** | `http_server_requests` P95 증가, CPU 낮음 | ❌ 효과 없음 | 경합 지점 제거 (이미 Redis로 옮김) |

### 함께 볼 k6 지표

| 지표 | 의미 |
| --- | --- |
| `http_req_waiting` | 서버 처리 시간. 이게 지배적이면 서버 내부 문제 |
| `http_req_blocked` / `connecting` | 커넥션 수립. 이게 크면 네트워크·커넥션 한계 |
| `iterations/s` | 실제 처리량. **VU를 늘려도 이게 안 오르면 포화** |
| P95 vs P99 격차 | 격차가 벌어지면 큐잉이 일어나고 있다 |

**핵심 판별식**: VU를 늘렸을 때 `iterations/s`가 오르지 않으면서 P95가 선형으로 증가하면,
어딘가에서 줄을 서고 있다는 뜻이다. 그 줄이 앱 안(CPU·스레드)이면 스케일아웃이 답이고,
앱 밖(Redis·DB)이면 스케일아웃은 도움이 안 된다.

---

## 시나리오 목록

| 커맨드 | 스크립트 | 용도 |
| --- | --- | --- |
| `users` | `generate-user-json.py` | 테스트 계정 생성 + JWT 발급 |
| `confirm` | `drop-confirm-entry-concurrency.js` | 입장 확정 동시성. `lock` 전 선행 필수 |
| `lock` | `drop-lock-concurrency.js` | 재고 선점 동시성 (성공/품절 개수 검증) |
| `oversell` | `drop-oversell-verification.js` | **초과 판매 검증** (서버 재고 대조 포함) |

`/enter`와 `/queue/rank`를 쓰던 `drop-enter-concurrency.js`, `drop-wait-active.js`는
대기열 제거와 함께 삭제됐다. 진입 흐름은 `today/drops → confirm-entry → lock-start`다.