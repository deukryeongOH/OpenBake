# OpenBake Performance Fixture - local/server 공용

## 지원 모드

### local

현재 로컬 구조:

```text
member-service : localhost:8081
core/backend   : localhost:8080
```

Core를 직접 호출하므로 `HeaderAuthenticationFilter`가 요구하는
`X-Openbake-*` identity header를 스크립트가 자동으로 넣습니다.

### server

서버 구조:

```text
client
  ↓
Nginx
  ↓
API Gateway
  ↓
member/core
```

따라서 공개 서버 주소 하나만 설정합니다.

```env
SERVER_BASE_URL=https://your-server.example.com
```

서버에서는 Bearer JWT를 Gateway가 검증합니다.

---

## 설치

이 압축파일의 `performance-test/prepare`를 프로젝트의 기존
`performance-test/prepare`에 덮어씁니다.

```bash
cd performance-test/prepare
chmod +x setup-seller.sh prepare-drop.sh

cp .env.perf.example .env.perf
cp .env.local.example .env.local
cp .env.server.example .env.server
```

`.env.server`에는 실제 주소를 입력하세요.

```env
SERVER_BASE_URL=https://...
```

---

## 로컬 사용

판매자 준비:

```bash
./setup-seller.sh local
```

100명용 새 Drop:

```bash
./prepare-drop.sh local 100
```

그 다음:

```bash
cd ..
./run-k6.sh users
./run-k6.sh enter
./run-k6.sh confirm
./run-k6.sh lock
```

---

## 서버 사용

서버 머신에 SSH 접속한 뒤 프로젝트에서:

```bash
cd performance-test/prepare

./setup-seller.sh server
./prepare-drop.sh server 100
```

이후:

```bash
cd ..
./run-k6.sh users
./run-k6.sh enter
./run-k6.sh confirm
./run-k6.sh lock
```

`prepare-drop.sh server`가 실행되면 `.env.k6`도 자동으로:

```env
CORE_BASE_URL=<SERVER_BASE_URL>
MEMBER_BASE_URL=<SERVER_BASE_URL>
DROP_ID=<새 ID>
USER_COUNT=<입력값>
```

으로 변경됩니다.

---

## 왜 server도 서버 머신에서 실행해야 하나?

현재 `POST /api/v1/drops/register` 응답에는 `dropId`가 없습니다.

또 정상 등록 API는 미래 슬롯을 요구하므로 성능테스트 직전에 바로 ACTIVE 상태로
만들려면 테스트 fixture 단계에서 DB 시각을 변경해야 합니다.

따라서 현재 버전은 다음 두 작업에 PostgreSQL container를 사용합니다.

1. 테스트 판매자 `APPROVED` 처리
2. 생성된 Drop ID 조회 및 즉시 ACTIVE 전환

이 부분은 서비스의 정상 비즈니스 로직을 변경하지 않기 위한 **성능테스트 fixture 전용 처리**입니다.

따라서 서버 PC 밖에서 `./prepare-drop.sh server`를 실행하는 방식은 기본 지원하지 않습니다.
서버에 SSH 접속해서 실행해야 합니다.

---

## 권장 사용 흐름

```bash
# 로컬 검증
./prepare-drop.sh local 100

# 서버 검증
./prepare-drop.sh server 100
```

같은 스크립트를 사용하므로 테스트 데이터 준비 방식 차이를 최소화할 수 있습니다.

## 주의

이 fixture는 local/dev 또는 별도 성능테스트 서버에서만 사용하세요.
운영 고객 데이터가 있는 production DB에 실행하지 마세요.
