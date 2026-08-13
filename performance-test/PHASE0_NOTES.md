# Phase 0 - Performance Test Baseline Cleanup

이번 단계의 목표는 Capacity/Load Test를 추가하기 전에 기존 동시성 테스트의 재현성과 안전성을 높이는 것입니다.

## 적용한 변경

1. `.env.k6`, `users.json`, `.env.monitoring`을 Git에서 제외하도록 로컬 `.gitignore` 추가
2. `run-k6.sh`에서 사용하지 않는 `PAYMENT_BASE_URL` 필수 검증 제거
3. 명령별로 실제 필요한 환경변수만 검증하도록 수정
4. 이미 `MEMBER_BASE_URL`, `CORE_BASE_URL`을 직접 읽는 코드에 맞춰 오래된 `BASE_URL=...` 전달 제거
5. `drop-enter-concurrency.js`에 성공/충돌/400 오류 개수 Threshold 추가
6. 짧은 Burst 테스트를 놓치지 않도록 Prometheus scrape/evaluation interval을 5초에서 1초로 조정
7. `.env.k6.example`을 실제 코드가 사용하는 환경변수 기준으로 정리

## 아직 변경하지 않은 것

### member-map.sql

`auths.id`를 직접 계산해 넣는 코드는 IDENTITY/SEQUENCE 컬럼이라면 sequence 불일치를 만들 수 있습니다.
다만 현재 압축파일에는 `auths` 테이블 DDL/Entity가 없으므로 이번 패치에서는 SQL을 임의 변경하지 않았습니다.
`auths.id` 생성 전략을 확인한 다음 수정하는 것이 안전합니다.

### Grafana / Capacity Test

이번 Phase 0에서는 기존 테스트 기반만 정리했습니다.
다음 단계에서 Grafana와 지속형 Capacity Test(`ramping-vus`)를 추가합니다.
