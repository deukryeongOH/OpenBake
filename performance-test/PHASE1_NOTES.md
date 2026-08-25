# Phase 1 - Concurrency Baseline Hardening

## 목표

현재 `enter -> confirm -> lock` 동시성 테스트를 Capacity Test의 기준선으로 사용할 수 있도록 검증 기준과 결과 저장 방식을 정리합니다.

## 변경 사항

1. 기능 검증(`check`)과 성능 NFR(`Trend threshold`)을 분리했습니다.
    - 기존에는 모든 요청이 2초/3초 안에 들어와야 `checks rate==1`을 만족해 P95/P99 기준보다 더 엄격한 별도 조건이 숨어 있었습니다.
    - 이제 `check`는 상태/비즈니스 결과만 검증하고, 응답시간은 P95/P99 threshold만 사용합니다.
2. `drop-enter`에 `http_req_failed: rate==0`을 추가했습니다.
3. `drop-lock`에도 `http_req_failed: rate==0`을 추가했습니다.
    - 정상 재고 부족 `DR018`(기존 DR007 응답도 호환 처리)은 기존 `responseCallback`에서 기대 가능한 400으로 처리합니다.
    - invalid state/lock timeout/unexpected는 사용자 정의 Counter threshold가 실패를 잡습니다.
4. `run-k6.sh`가 실행마다 결과 디렉터리를 자동 생성합니다.
5. 실행 결과를 `console.txt`, `summary.json`, `metadata.env`로 함께 저장합니다.
6. 결과 메타데이터에 사용자 수, Drop ID, 기대 성공/품절 수, k6 버전, Git commit을 남깁니다.

## 이번 단계에서 하지 않은 것

- `enter -> confirm -> lock` 자동 연쇄 실행은 추가하지 않았습니다.
    - Queue Active 전환 완료 여부를 확인할 API/조건이 현재 제공된 코드에 없기 때문입니다.
    - Active 전환을 임의 sleep으로 대체하면 대규모 테스트에서 잘못된 결과를 만들 수 있습니다.
- `member-map.sql`의 `auths.id` 직접 생성 문제는 Entity/DDL 확인 전까지 보류합니다.
- ramp-up/duration을 가진 Capacity Test는 다음 단계에서 별도 스크립트로 추가합니다.
- Grafana는 Monitoring 단계에서 추가합니다.

## Phase 1 완료 기준

동일한 Drop/test data 조건에서 `enter`, `confirm`, `lock`을 실행했을 때:

- 기능 Counter threshold가 모두 통과
- 예상하지 않은 오류 0
- P95/P99 NFR 확인
- 실행별 `summary.json` 및 `metadata.env` 생성

이 상태가 확보되면 다음 단계에서 Grafana와 서버 지표 확인을 먼저 완성한 뒤, `ramping-vus` 기반 Capacity Test로 확장합니다.

## Drop 유저 플로우

기존의 개별 Drop 테스트를 한 사용자의 실제 흐름으로 연결한 시나리오입니다.

```text
GET  /api/v1/drops/{dropId}/info
  -> POST /api/v1/drops/{dropId}/enter
  -> GET  /api/v1/drops/{dropId}/queue/rank (ACTIVE까지 polling)
  -> POST /api/v1/drops/{dropId}/confirm-entry
  -> POST /api/v1/drops/{dropId}/lock-start
```

주문/장바구니/결제는 이 시나리오에 포함하지 않습니다.

### 1. 테스트 데이터 준비

```bash
cd performance-test/prepare
./prepare-drop.sh local 1
```

사용자 수를 늘릴 때는 `1`을 원하는 수로 변경합니다.

### 2. Drop 유저 플로우 실행

```bash
cd ..
./run-k6.sh local flow
```

### 3. 기존 단계별 테스트

기존 테스트도 그대로 사용할 수 있습니다.

```bash
./run-k6.sh local enter
./run-k6.sh local wait-active
./run-k6.sh local confirm
./run-k6.sh local lock
```

### 주요 유저 플로우 메트릭

- `drop_user_flow_success`: 전체 Drop 흐름 성공 사용자 수
- `drop_user_flow_failed`: 중간 단계 실패 사용자 수
- `drop_flow_info_duration`: 드롭 상세 조회 시간
- `drop_flow_enter_duration`: 대기열 진입 시간
- `drop_flow_wait_active_duration`: ACTIVE가 되기까지의 사용자 대기 시간
- `drop_flow_confirm_duration`: 입장 확정 시간
- `drop_flow_lock_duration`: 재고 선점 시간
- `drop_flow_total_duration`: 한 사용자의 전체 Drop 흐름 시간

각 HTTP 요청에는 `test_type=drop-user-flow`와 `flow_step` 태그가 들어갑니다.
