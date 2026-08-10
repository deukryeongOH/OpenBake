# OpenBake Performance Test

OpenBake 드롭 구매 흐름의 동시성 및 성능을 검증하기 위한 k6 테스트입니다.

## 1. 테스트 흐름

```text
대기열 진입
POST /api/v1/drops/{dropId}/enter
        ↓
Queue Active 전환
        ↓
입장 확정
POST /api/v1/drops/{dropId}/confirm-entry
        ↓
재고 선점
POST /api/v1/drops/{dropId}/lock-start
```

각 VU(Virtual User)는 서로 다른 테스트 회원과 JWT를 사용합니다.

---

## 2. 파일 구성

```text
performance-test/
├── .env.k6
├── .env.k6.example
├── generate-user-json.py
├── k6-users.js
├── drop-enter-concurrency.js
├── drop-confirm-entry-concurrency.js
├── drop-lock-concurrency.js
├── run-k6.sh
├── users.json
└── results/
```

`.env.k6`, `users.json`은 Git에 커밋하지 않습니다.

---

## 3. 환경 설정

```bash
cp .env.k6.example .env.k6
```

`.env.k6` 예시:

```env
BASE_URL=http://192.168.115.116:8080
DROP_ID=10

USER_COUNT=100
START_INDEX=1
QUANTITY=1

EXPECTED_SUCCESS=100
EXPECTED_SOLD_OUT=0

LOGIN_PATH=/api/v1/auth/login
TEST_PASSWORD=테스트_비밀번호
TOKEN_PATH=data.accessToken

EMAIL_PREFIX=loadtest
EMAIL_DOMAIN=naver.com

OUTPUT_FILE=users.json
REQUEST_TIMEOUT=10
```

WSL 환경에서는 IP가 변경될 수 있으므로 테스트 전에 `BASE_URL`을 확인합니다.

---

## 4. 테스트 회원 준비

`USER_COUNT`만큼 테스트 회원이 DB에 존재해야 합니다.

100명 예시:

```text
loadtest0001@naver.com
...
loadtest0100@naver.com
```

1000명 예시:

```text
loadtest0001@naver.com
...
loadtest1000@naver.com
```

---

## 5. users.json 생성

```bash
chmod +x run-k6.sh
./run-k6.sh users
```

각 테스트 회원으로 로그인하여 JWT를 발급받고, JWT의 `sub`를 `memberId`로 사용하여 `users.json`을 생성합니다.

검증:

```bash
python3 - <<'PY'
import json

with open("users.json", encoding="utf-8") as f:
    users = json.load(f)

print("사용자 수:", len(users))
print("JWT 없는 사용자:", sum(not u.get("token") for u in users))
print("memberId 없는 사용자:", sum(u.get("memberId") is None for u in users))
PY
```

정상 예시:

```text
사용자 수: 100
JWT 없는 사용자: 0
memberId 없는 사용자: 0
```

---

## 6. 테스트 실행

### 대기열 진입

```bash
./run-k6.sh enter
```

기대값:

```text
drop_enter_success = USER_COUNT
drop_enter_unexpected = 0
```

### Queue Active 전환

`enter` 이후 사용자가 실제 Active 상태로 전환되었는지 확인합니다.

```text
enter
  ↓
Queue Active 확인
  ↓
confirm
```

특히 대규모 테스트에서는 Active 전환이 완료되기 전에 `confirm`을 실행하지 않도록 주의합니다.

### 입장 확정

```bash
./run-k6.sh confirm
```

기대값:

```text
confirm_entry_success = USER_COUNT

confirm_entry_unauthorized = 0
confirm_entry_drop_not_found = 0
confirm_entry_unexpected = 0
```

### 재고 선점

`confirm`이 모두 성공한 것을 확인한 후 실행합니다.

```bash
./run-k6.sh lock
```

재고가 충분한 경우:

```text
drop_lock_success = USER_COUNT

drop_lock_timeout = 0
drop_lock_invalid_state = 0
drop_lock_unexpected = 0
```

`confirm_entry_success != USER_COUNT`라면 `lock`을 진행하기 전에 Queue 상태를 확인합니다.

---

## 7. 성능 기준

| API | P95 | P99 |
|---|---:|---:|
| Enter | < 1초 | < 2초 |
| Confirm | < 1초 | < 2초 |
| Lock | < 1.5초 | < 3초 |

공통 기준:

```text
Unexpected Error = 0
Lock Timeout = 0
HTTP 5xx = 0
```

---

## 8. 재고 부족 시나리오

재고 3개에 사용자 5명이 동시에 접근하는 경우:

```env
USER_COUNT=5
QUANTITY=1

EXPECTED_SUCCESS=3
EXPECTED_SOLD_OUT=2
```

기대 결과:

```text
drop_lock_success     = 3
drop_lock_sold_out    = 2
drop_lock_timeout     = 0
drop_lock_unexpected  = 0
```

품절은 정상적인 비즈니스 결과이며 시스템 오류로 판단하지 않습니다.

추가로 DB에서 다음을 검증합니다.

```text
최종 재고 >= 0
초과판매 없음
중복 선점 없음
```

---

## 9. 현재 테스트 결과

| 동시 사용자 | Enter | Confirm | Lock |
|---:|---|---|---|
| 100 | ✅ | ✅ | ✅ |
| 200 | ✅ | ✅ | ✅ |
| 500 | ✅ | ✅ | ❌ P95/P99 초과 |
| 600 | ✅ | ✅ | ❌ P95/P99 초과 |
| 700 | ✅ | ✅ | ❌ P95/P99 초과 |
| 1000 | ❌ P95 초과 | ❌ 417건 DR009 | ⚠️ Confirm 실패 영향 |

현재까지 재고 선점 API는 500명 이상의 동시 요청에서 성능 기준을 초과했습니다.

1000명 테스트에서는:

```text
Enter
1000 / 1000 성공

Confirm
583 성공
417 DR009

Lock
583 성공
417 DR011
```

이 발생했습니다.

`DR011` 417건은 Lock 자체의 장애가 아니라 앞 단계에서 `confirm-entry`에 실패한 사용자가 Lock을 요청하면서 발생한 결과입니다.

따라서 1000명 테스트에서는 다음 순서를 반드시 확인합니다.

```text
1000명 enter 성공
        ↓
1000명 Queue Active 확인
        ↓
1000명 confirm 성공 확인
        ↓
lock 테스트
```

---

## 10. 현재 결론

```text
100 ~ 200명
→ 전체 성능 기준 통과

500 ~ 700명
→ 재고 선점 기능은 정상 동작
→ Lock 응답시간 성능 기준 초과

1000명
→ Enter P95 기준 초과
→ Queue Active 미완료로 Confirm 417건 실패
→ 1000명의 순수 Lock 성능은 재측정 필요
```

현재 주요 성능 병목 후보는 **동시 요청 증가에 따른 재고 선점 Lock 대기 시간**입니다.