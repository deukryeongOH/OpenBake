# wait-active 추가 파일

## 목적

`enter` 성공 직후 `confirm-entry`를 바로 호출하면 일부 사용자가 아직 WAITING 큐에 남아 있어:

```text
DR009
드롭에 입장 할 수 없습니다. 조금만 더 기다려주세요.
```

가 발생할 수 있습니다.

이 스크립트는 모든 테스트 사용자의 queue rank를 polling해서 `rank == 0`이 될 때까지 기다립니다.

## 적용 파일

```text
performance-test/
├── drop-wait-active.js
└── run-k6.sh
```

`.env.k6`에는 다음 값을 추가하세요.

```env
WAIT_ACTIVE_POLL_MS=200
WAIT_ACTIVE_TIMEOUT_SECONDS=30
```

## 실행 순서

```bash
cd performance-test

./run-k6.sh users
./run-k6.sh enter
./run-k6.sh wait-active
./run-k6.sh confirm
./run-k6.sh lock
```

## local

```env
CORE_BASE_URL=http://localhost:8080
MEMBER_BASE_URL=http://localhost:8081
AUTH_MODE=direct
TEST_MEMBER_ROLE=CUSTOMER
```

## server

```env
CORE_BASE_URL=https://서버주소
MEMBER_BASE_URL=https://서버주소
AUTH_MODE=gateway
```

## 성공 기준

100명 테스트라면:

```text
wait_active_success = 100
wait_active_timeout = 0
checks = 100%
```

가 되어야 합니다.

## 조정

1000명 이상에서 큐 전환이 오래 걸리면:

```env
WAIT_ACTIVE_TIMEOUT_SECONDS=60
```

처럼 늘릴 수 있습니다.

polling 간격은 QueueScheduler의 현재 200ms 주기에 맞춰 기본 200ms로 설정했습니다.
