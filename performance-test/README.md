# OpenBake wait-active 수정본

최신 코드의 실제 대기순번 API:

```text
GET /api/v1/drops/{dropId}/queue/rank
```

기존 생성본은 잘못된 `/api/v1/drops/{dropId}/rank`를 호출해 `404 C003`이 발생했습니다.

## 적용

기존 파일을 덮어쓰세요.

```text
performance-test/drop-wait-active.js
```

그 뒤:

```bash
cd performance-test
./run-k6.sh wait-active
```

정상적으로 100명이 ACTIVE라면:

```text
wait_active_success = 100
wait_active_timeout = 0
wait_active_unexpected = 0
checks = 100%
```

그 다음:

```bash
./run-k6.sh confirm
./run-k6.sh lock
```

을 진행합니다.
