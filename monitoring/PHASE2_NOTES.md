# Phase 2 - Prometheus + Grafana Observability

## 완료 목표

Capacity Test 전에 부하 지표와 서버 내부 지표를 동일 시간축으로 관찰할 수 있게 합니다.

## 변경 사항

1. `Grafana` 컨테이너를 monitoring compose에 추가했습니다.
2. Prometheus datasource를 파일 provisioning으로 자동 등록합니다.
3. `OpenBake Performance Overview` dashboard를 코드로 provisioning합니다.
4. JVM / HTTP / Tomcat / Hikari 패널을 추가합니다.
5. k6 Prometheus Remote Write를 선택적으로 사용할 수 있게 `run-k6.sh`를 확장합니다.
6. 각 테스트 실행에 `testid=<run_id>` tag를 자동 부여합니다.
7. `verify-monitoring.sh`를 추가하여 Prometheus, Grafana, scrape target 상태를 확인합니다.
8. Grafana 관리자 비밀번호를 `.env.monitoring`으로 분리하고 기본 비밀번호 사용을 막습니다.

## 이번 단계에서 하지 않은 것

- PostgreSQL exporter / Redis exporter / node-exporter는 아직 추가하지 않습니다.
- DB lock/slow query/Redis latency는 Capacity Test에서 실제 병목 후보가 필요할 때 추가합니다.
- k6 Remote Write는 experimental output이므로 기본 비활성화합니다.
- Capacity Test 자체는 Phase 3에서 별도 스크립트로 추가합니다.

## Phase 2 완료 기준

- Prometheus `/targets`에서 필요한 OpenBake 서비스가 UP
- Grafana가 자동으로 Prometheus datasource를 인식
- `OpenBake Performance Overview` dashboard가 자동 생성
- 테스트 중 HTTP/JVM/Tomcat/Hikari 지표가 움직이는 것을 확인
- 필요 시 k6 Remote Write를 켜고 같은 시간축에서 VU/P95/P99를 확인
