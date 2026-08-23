# 외부 Go 이후 Rollback — 역이전 (수동, 자동화하지 않음)

설계 근거: [`docs/k3s-learning/12-compose-to-k3s-cutover-rollback-plan.md`](../../../docs/k3s-learning/12-compose-to-k3s-cutover-rollback-plan.md) 11.2장

> k3s가 한 건이라도 write를 받았다면 Compose DB는 이미 뒤처진 사본이다. old Compose를 바로 켜면 주문·회원·결제 데이터가 되돌아가거나 두 갈래로 나뉜다.

## 먼저 확인할 것 — 정말 역이전이 필요한가

우선순위는 **k3s에서 forward-fix**다. 아래에 해당하지 않으면 역이전하지 말고 forward-fix를 시도하라.

- 심각한 데이터 정합성 문제 (예: 결제 금액 불일치, 중복 차감)
- 지속적인 전체 5xx (일시적 단일 Pod 문제가 아님)
- 결제 correctness가 훼손되어 forward-fix로 복구 불가능
- 데이터 손상 또는 장시간 복구 불가능

이 경로는 downtime이 길고 rehearsal되지 않은 상황에서는 위험하다. **외부 Go는 되돌리기 쉬운 토글이 아니라 데이터 원본이 바뀌는 승인 지점이다.** 역이전 실행은 담당자와 Go/No-Go 최종 승인자의 명시적 승인을 받은 뒤에만 시작한다.

## 역이전 6단계

이 문서는 실행 스크립트가 아니다 — 각 단계에서 사람이 상태를 확인하고 다음 단계로 넘어갈지 판단한다.

### 1. 외부 트래픽과 k3s write를 다시 중지한다

- k3s Ingress 비활성화 (`kubectl -n openbake delete -k k8s/openbake/entrypoint`)
- api-gateway/backend/member-service/payment-service/ai-service를 scale down 하거나 중지해 k3s DB에 대한 write를 막는다

### 2. k3s 세 DB(실제로는 core/member/payment/ai 4개)의 final dump와 checksum을 생성한다

`scripts/cutover/01-freeze-and-dump.sh`와 같은 절차(`pg_dump -Fc --no-owner --no-privileges` + SHA-256 checksum)를 k3s 쪽 DB에 대해 반복한다. 스크립트를 그대로 재사용하지 말고 — 대상이 k3s이므로 host/port/credential을 k3s 쪽으로 바꿔 **손으로** 실행하며 각 단계를 확인한다.

### 3. Compose 대상 DB를 별도로 backup한다

되돌리기 전에 현재 Compose DB 상태도 보존해 둔다. 역이전이 잘못됐을 때 마지막으로 되짚어볼 지점이 필요하다.

### 4. k3s dump를 Compose DB에 restore한다

- 대상 Compose DB가 비어 있는 상태인지, 아니면 기존 데이터를 지우고 시작해야 하는지 먼저 판단한다
- `pg_restore --no-owner --no-privileges`로 복원
- restore log에서 error 확인

### 5. 데이터·sequence·결제 상태를 다시 검증한다

`scripts/cutover/verify-queries.sql`을 Compose DB에 대해 그대로 실행할 수 있다. 특히 sequence 검증(3번 항목)을 반드시 다시 확인한다 — 놓치면 재개 직후 PK 충돌이 난다.

### 6. k3s Ingress를 닫은 상태에서 Compose application과 nginx를 재가동한다

`scripts/cutover/rollback/before-open.sh`의 STEP 3~4와 동일한 방식으로 재가동하고 외부 HTTPS·핵심 기능을 검증한다.

## 역이전 후 반드시 남길 기록

- 역이전을 시작한 시각과 사유
- 역이전 중 발견된 데이터 불일치(있다면 목록)
- 결제 reconciliation 결과 — Toss 결제 내역과 Compose DB 상태를 다시 맞춰본 결과
- 승인자 이름과 승인 시각
