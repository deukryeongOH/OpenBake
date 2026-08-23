# Compose → k3s Cutover — 적용 산출물

설계 근거: [`docs/k3s-learning/12-compose-to-k3s-cutover-rollback-plan.md`](../../docs/k3s-learning/12-compose-to-k3s-cutover-rollback-plan.md)

이슈 5다. **운영 데이터를 옮기고 외부 트래픽을 전환하는 작업이라 실행은 전부 사람이 한다.** 여기 있는 것은 사람이 그대로 따라 실행할 수 있는 runbook과 스크립트이고, 어떤 스크립트도 운영 환경에서 실행되지 않았다.

## 무엇을 만드는가

```text
scripts/cutover/
├── 01-freeze-and-dump.sh     Compose 쓰기 동결 → 4개 DB final dump → checksum 기록
├── 02-restore-and-verify.sh  k3s restore(core→member→payment→ai) → verify-queries.sql 실행
├── verify-queries.sql        구조/데이터/ID(sequence)/관계 검증 — Compose·k3s 양쪽에서 동일하게 실행
├── smoke-test.sh             내부 인증·조회·주문(비결제)·결제상태 확인 + /internal, /actuator 노출 차단 확인
└── rollback/
    ├── README.md             두 rollback 경로 개요
    ├── before-open.sh        외부 Go 이전 — 빠른 rollback (Compose DB 복원 없음)
    └── after-open.md         외부 Go 이후 — 역이전 6단계, 자동화하지 않고 문서로만
```

## 시작 전 확인 — 이슈 1~4 전제

| 항목 | 상태 |
| --- | --- |
| k3s 클러스터 Node A/B Ready | 이슈 2(#203) 머지됨 |
| 데이터 계층(PostgreSQL 4종·Redis·ES·Kafka)이 k3s에서 Ready | 이슈 3(#205) 머지됨 |
| 애플리케이션 5개 서비스 port-forward 동작 확인 | 이슈 3 범위 — 실제 클러스터에서 사람이 재확인 필요 |
| Prometheus가 5개 서비스 수집, HPA metric 정상 | 이슈 4(#207) 머지됨 |
| PostgreSQL backup CronJob 동작, restore drill 1회 완료 | CronJob은 `k8s/openbake/jobs/postgres-backup/`에 존재 — **restore drill 완료 여부는 이 저장소 산출물만으로 확인 불가, 담당자가 실측 후 표시**할 것 |

> `docs/k3s-learning/issues.md`의 진행 상태 표는 이슈 2~4를 "미착수"로 표시하지만 git 이력상 실제로는 머지되어 있다(PR #203/#205/#207). 표 갱신이 필요하다.

Elasticsearch 전체 재색인은 `/internal/v1/products/reindex`(POST) 관리자 endpoint로 트리거한다. 호출은 `./03-reindex.sh`가 담당한다.

```bash
ADMIN_MEMBER_ID=<ROLE_ADMIN 회원 id> ./03-reindex.sh
```

이 endpoint는 **두 겹으로 보호된다.**

1. 외부 Ingress(`k8s/openbake/entrypoint/ingress.yaml`)가 `/internal/**`을 라우팅하지 않는다
2. `SecurityConfig.java`의 `.requestMatchers("/internal/v1/**").hasRole("ADMIN")`

따라서 Gateway를 거치는 경로로는 호출할 수 없다. 스크립트는 backend로 직접 `port-forward`한 뒤 `HeaderAuthenticationFilter`가 신뢰하는 신원 header 3개를 붙인다.

```text
X-Openbake-Member-Id     : ADMIN_MEMBER_ID
X-Openbake-Member-Role   : ADMIN
X-Openbake-Auth-Source   : api-gateway
```

`ADMIN_MEMBER_ID`는 **실제로 존재하는 `ROLE_ADMIN` 회원의 id**여야 한다. 없으면 401(신원 거부) 또는 403(권한 부족)이 나며 스크립트가 그 둘을 구분해 알려준다. 전환 전에 미리 확보해 아래 실측 기록표에 적어둔다.

코드: `src/main/java/com/openbake/product/presentation/AdminProductReindexController.java`, `src/main/java/com/openbake/product/infrastructure/elasticsearch/ProductReindexScheduler.java`

### 실행 환경

`scripts/cutover/`의 스크립트는 **Node A(Linux)에서 실행하는 것을 전제로 한다.** bash 4 문법(`${var,,}` 등)과 GNU coreutils를 사용한다. macOS 기본 bash 3.2에서는 동작하지 않는다.

## ai-service·ai-postgres 처리 방침

12번 문서는 `core/member/payment` 세 DB만 다루지만 **ai-postgres는 실제로 존재한다.** `docker-compose.yaml`의 `ai-postgres`(볼륨 `openbake-ai-pg-data`, `pgvector/pgvector`), `k8s/openbake/data/ai-postgres/`, `k8s/openbake/jobs/postgres-backup/cronjob-ai.yaml`이 이미 있다.

- **ai-postgres는 4번째 이전 대상으로 취급한다.** `01-freeze-and-dump.sh`·`02-restore-and-verify.sh`·`verify-queries.sql` 모두 core/member/payment와 동일하게 다룬다.
- **ai-service는 Kafka consumer로 이 DB에 쓴다** (`ProductEmbeddingTask`, `MemberProductInteraction`, `MemberDeletionMarker` 등). 12번 문서 4.1장의 "write 가능한 애플리케이션 중지" 목록(`api-gateway`/`backend`/`member-service`/`payment-service`)에 ai-service가 빠져 있는데, consumer가 계속 살아 있으면 freeze 이후에도 ai-postgres에 write가 발생할 수 있다. **이번 산출물은 ai-service도 freeze 대상 애플리케이션에 포함시켰다.** (12번 문서와 다르게 작성한 부분)
- ai-postgres는 pgvector extension이 필요하다. restore 전에 `CREATE EXTENSION IF NOT EXISTS vector;`를 실행한다.
- ai 도메인 테이블(`product_embedding_metadata`, `member_product_interaction`, `member_deletion_marker`, `product_embedding_task`)은 서비스 추천 품질에는 영향 있지만 주문·결제 정합성과 무관하므로 `verify-queries.sql`의 핵심 테이블(사람 확인 필수) 범위에는 넣지 않았다. row count·extension 존재만 구조 검증으로 확인한다.

## 공통 환경 변수

DB별 접속 정보는 스크립트에 하드코딩하지 않고 실행 시 환경 변수로 주입한다. 코드는 `<KEY>`를 `CORE`/`MEMBER`/`PAYMENT`/`AI`로 치환해 읽는다.

### `01-freeze-and-dump.sh` (Compose 측)

| 변수 | 기본값 | 비고 |
| --- | --- | --- |
| `<KEY>_COMPOSE_DB_HOST` | `127.0.0.1` | Node A에서 직접 실행 전제 |
| `<KEY>_COMPOSE_DB_PORT` | core `5432` / member `5434` / payment `5435` / ai `5436` | `docker-compose.yaml` 포트 매핑과 동일 |
| `<KEY>_COMPOSE_DB_NAME` | core `openbake` / member `openbake_member` / payment `openbake_payment` / ai `openbake_ai` | |
| `<KEY>_COMPOSE_DB_USER` | core/member/payment `openbake` | ai는 기본값 없음(compose `.env`의 `DB_USERNAME`) — 필수 입력 |
| `<KEY>_COMPOSE_DB_PASSWORD` | 없음(필수) | `PGPASSWORD`로 스크립트 내부에 전달, 하드코딩 금지 |
| `DUMP_DIR` | `/opt/openbake/cutover/dumps/<timestamp>` | `chmod 700`으로 생성 |
| `COMPOSE_FILE` | `docker-compose.yaml:docker-compose.prod.yaml` | 애플리케이션 중지에 사용 |
| `S3_BUCKET`, `AWS_REGION` | 없음(선택) | 비어 있으면 S3 업로드 단계를 건너뛰고 수동 업로드를 안내 |

### `02-restore-and-verify.sh` (k3s 측)

| 변수 | 기본값 | 비고 |
| --- | --- | --- |
| `KUBE_NAMESPACE` | `openbake` | |
| `<KEY>_K3S_DB_NAME` | Compose와 동일 | `k8s/openbake/jobs/postgres-backup/cronjob-*.yaml`과 일치 확인함 |
| `PORT_FORWARD_BASE_PORT` | `15432` | core/member/payment/ai 순서로 `+0,+1,+2,+3` |

DB 사용자·비밀번호는 env var로 받지 않고 `kubectl -n "$KUBE_NAMESPACE" get secret <key>-db-credentials -o jsonpath=...`로 클러스터에서 직접 읽는다. 이미 존재하는 Secret을 재사용하므로 별도 입력·중복 보관이 필요 없다.

## Rehearsal 절 vs 본 전환 절

명령은 완전히 같고 **데이터만 다르다.**

- **Rehearsal**: `01-freeze-and-dump.sh`를 운영 Compose에 대해 그대로 실행하되, 이건 **non-final dump**다(실제 전환의 최종 데이터가 아니다). k3s의 연습용 빈 DB에 `02-restore-and-verify.sh`로 복원한다. 이 시점에는 외부 entrypoint를 열지 않는다 — 검증은 `kubectl port-forward` 또는 cluster 내부 임시 Pod로만 한다.
- 측정해서 아래 표에 기록할 값: DB별 dump/restore 시간과 크기, 전체 소요시간. 두 스크립트 모두 각 단계 종료 시 소요시간을 출력하고 `manifest.txt`에 남긴다.

| 시각·소요시간 항목 | 계획(12번 문서 2장) | Rehearsal 실측 |
| --- | ---: | --- |
| 점검 모드·Compose write 중지 | 10분 | |
| PostgreSQL 4개 final dump | 10~20분 | |
| k3s PostgreSQL restore | 15~30분 | |
| 데이터·서비스 내부 검증 | 20~30분 | |
| nginx→Traefik·TLS 전환 | 10~20분 | |
| 여유·의사결정 | 25~30분 | |

rehearsal에서 운영 결제 승인을 실제로 발생시키지 않는다.

## 본 전환 절 — 실행 순서

```text
1. Go 조건(아래 체크리스트) 확인
2. ./01-freeze-and-dump.sh          (Compose 쓰기 동결 + final dump, 각 단계 확인 후 진행)
3. ./02-restore-and-verify.sh       (k3s restore + verify-queries.sql 실행)
4. ADMIN_MEMBER_ID=<id> ./03-reindex.sh   (Elasticsearch 전체 재색인, 완료까지 검색 비공개 유지)
5. ./smoke-test.sh                  (내부 port-forward 기준 smoke test)
6. Compose nginx 종료 확인
7. Traefik 외부 entrypoint·Ingress 활성화 — 명령은 `k8s/README.md`의 "cutover 당일 순서" 참고(중복 작성하지 않음)
8. 외부 HTTPS smoke test (./smoke-test.sh --external 또는 동일 curl을 공인 도메인으로)
9. 결제 Webhook reconciliation (아래 절 참고)
10. Open 조건(아래 체크리스트) 확인 후 관찰 시작
```

## 기동 순서 (12번 문서 8장)

```text
1. member-service, payment-service
2. backend
3. api-gateway
4. port-forward / cluster 내부 smoke test  → smoke-test.sh
5. Compose nginx 종료 확인
6. Traefik 외부 entrypoint와 Ingress 활성화 → k8s/README.md 참고
7. ACME 인증서 발급 확인
8. 외부 HTTPS smoke test
```

DB·Redis·Elasticsearch는 이 순서보다 먼저 Ready여야 한다.

## Redis 초기화와 JWT 회전 (12번 문서 6장 — B안 확정)

```text
1. cutover 전 새 JWT secret 생성 → k3s Secret에만 준비
2. Compose 중지 전까지 기존 JWT secret 유지 (Compose 쪽에 새 secret을 미리 반영하지 않는다)
3. k3s Redis는 빈 PVC로 시작 (RDB 옮기지 않음)
4. api-gateway·member-service에 동일한 새 JWT secret 주입
5. 두 Pod가 같은 Secret revision으로 Ready인지 확인 후 외부 오픈
6. 전환 공지에 전체 재로그인 필요를 포함
```

Redis만 비우고 JWT secret을 유지하면 안 된다 — 아직 만료되지 않은 과거 access token을 새 Redis가 blacklist로 판단하지 못한다. **두 작업은 반드시 함께 한다.** `SETTLEMENT_ENCRYPTION_KEY`는 회전하지 않는다.

## 결제 Webhook 주의사항 (12번 문서 9장)

Toss webhook은 10초 내 200 응답, 실패 시 최대 7회 재전송. 재전송이 있다고 정합성 검증을 대신할 수 없다.

- [ ] 점검 직전 진행 중 결제 상태 확인·기록
- [ ] 전환 후 webhook 수신 log와 HTTP status 확인
- [ ] Toss 결제 내역과 내부 payment 상태 reconciliation
- [ ] webhook handler 중복 수신 idempotency 확인
- [ ] 미처리 상태를 운영자가 목록으로 남겨 재확인

## Go/No-Go 체크리스트 (12번 문서 10장)

### 점검 시작 전 Go 조건

- [ ] rehearsal dump/restore 성공 및 실제 소요시간 기록
- [ ] 4개 DB(core/member/payment/ai) final backup을 저장할 private S3 준비
- [ ] Node A/B와 k3s 시스템 Pod 정상
- [ ] root EBS 사용률과 final dump 임시 공간 충분
- [ ] 고정 공인 IP와 ACME email 확인
- [ ] 배포할 image tag가 아니라 digest 기록
- [ ] 실제 Secret과 registry credential 준비
- [ ] Elasticsearch 전체 재색인 endpoint(`/internal/v1/products/reindex`) 검증
- [x] Redis 초기화 + JWT secret 회전 확정
- [ ] 점검·재로그인 가능성 사용자 공지

### 외부 트래픽 Open 조건

- [ ] 4개 DB restore와 데이터 비교 통과
- [ ] Elasticsearch 재색인·검색 검증 통과
- [ ] 모든 필수 Pod Ready, 재시작 반복 없음
- [ ] 내부 smoke test 통과
- [ ] Gateway 인증과 신원 header 전달 정상
- [ ] `/internal/**`과 내부 Service가 외부에서 노출되지 않음
- [ ] NetworkPolicy 적용 후 필요한 통신 정상
- [ ] Prometheus target과 HPA metric 정상
- [ ] Traefik HTTPS와 인증서 정상
- [ ] 결제 webhook·상태 reconciliation 가능

핵심 데이터·인증·결제 조건이 하나라도 실패하면 외부 트래픽을 열지 않는다. **이 판단은 스크립트가 아니라 사람이 한다** — 모든 스크립트는 결과를 출력만 하고 Go/No-Go를 자동 판정하지 않는다.

## 사람의 확인을 받는 지점 (전체 스크립트 공통)

각 스크립트는 `set -Eeuo pipefail`로 실패 시 즉시 중단하며, 아래 지점에서 `read -r -p`로 사람의 확인을 기다린다.

- `01-freeze-and-dump.sh`: 점검 공지 완료 확인 / 결제 흐름 중단 확인 / 애플리케이션(ai-service 포함 5개) 중지 실행 직전 / write 중단 확인 / DB별 dump 시작 직전 / S3 업로드 실행 직전(선택)
- `02-restore-and-verify.sh`: Pod Ready 확인 / 대상 DB가 비어 있는지 확인 / role·extension 준비 실행 직전 / DB별 restore 시작 직전 / verify-queries.sql 결과 확인 후 다음 DB로 진행
- `smoke-test.sh`: 외부 대상으로 전환할 때(`--external`) 실행 전 1회 확인
- `rollback/before-open.sh`: Ingress 비활성화 실행 직전 / Compose 재가동 실행 직전

## 기존 Compose 데이터 보존

전환 직후 Docker volume을 삭제하지 않는다. PostgreSQL volume 4개(core/member/payment/**ai 포함**)와 Elasticsearch volume을 7일 보존한다.

Node A의 50Gi root EBS에 Compose 원본과 k3s PVC가 공존하므로 매일 `df -h /` 로 사용률을 확인한다. 80%를 넘으면 자동 삭제하지 말고 final dump·restore 검증과 담당자 승인을 거쳐 오래된 Compose volume부터 정리한다.

## Rollback

`rollback/README.md` 참고. 외부 Go 이전/이후로 절차가 완전히 다르다.
