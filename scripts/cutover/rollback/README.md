# Rollback — 외부 Go 시점에 따라 절차가 완전히 다르다

설계 근거: [`docs/k3s-learning/12-compose-to-k3s-cutover-rollback-plan.md`](../../../docs/k3s-learning/12-compose-to-k3s-cutover-rollback-plan.md) 11~12장

| 시점 | 원본(Source of Truth) | 산출물 |
| --- | --- | --- |
| 외부 Go **이전** | 여전히 Compose PostgreSQL | `before-open.sh` — 자동화된 빠른 rollback |
| 외부 Go **이후** | k3s PostgreSQL (write를 받았다면) | `after-open.md` — 자동화하지 않은 수동 절차 |

## 왜 이후는 자동화하지 않는가

k3s가 한 건이라도 write를 받았다면 Compose DB는 이미 뒤처진 사본이다. 단순 재가동은 주문·회원·결제 데이터가 되돌아가거나 두 갈래로 나뉘는 결과를 낳는다. 우선순위는 **k3s에서 forward-fix**이고, 역이전은 데이터 손상이나 장시간 복구 불가일 때만 하는 최후 수단이라 실수로 자동 실행되면 안 된다. 그래서 `after-open.md`는 실행 가능한 스크립트가 아니라 사람이 각 단계에서 판단하며 손으로 따라가는 문서다.

## Go 판단 기준 (12번 문서 12장)

외부 Open **전** — 아래 중 하나라도 발생하면 즉시 rollback (`before-open.sh`):

- DB dump/restore error 또는 핵심 row count 불일치
- sequence·constraint·extension 오류
- 로그인·Gateway 인증 실패
- drop·주문·결제 핵심 흐름 실패
- image pull, CrashLoopBackOff, 지속적인 probe 실패
- Node DiskPressure/MemoryPressure 또는 예상 밖 OOMKilled
- Traefik/TLS가 정한 제한 시간(초기 10분 권장) 안에 준비되지 않음
- webhook을 정상 수신·검증할 수 없음

외부 Open **후** — 일시적인 단일 Pod 문제만으로 즉시 역이전하지 않는다. 심각한 데이터 정합성 문제, 지속적인 전체 5xx, 결제 correctness 훼손처럼 forward-fix가 어려운 문제에서만 `after-open.md`의 역이전을 검토한다.

이 판단은 사람이 한다. 두 산출물 모두 Go/No-Go를 자동으로 결정하지 않는다.
