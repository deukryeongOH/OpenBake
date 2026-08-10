-- ============================================================================
-- Order.buyer_name / Order.seller_phone_number 백필 스크립트
-- ============================================================================
--
-- 배경:
--   refactor/member-service-physical-seperation 브랜치(PR #___)에서
--   orders 테이블에 buyer_name, seller_phone_number 컬럼을 NOT NULL로 추가했다.
--   같은 브랜치에서 docker-compose.yaml에 member-service 전용 member-postgres
--   컨테이너도 추가됐지만, 이건 인프라(빈 컨테이너)만 준비된 상태이고 실제
--   회원 데이터 이전은 아직 하지 않았다(별도 후속 작업). 그래서 로컬에서
--   root/member-service를 개별 프로세스로 띄워 테스트하는 지금 시점에는
--   member/seller/orders가 여전히 같은 DB(기존 openbake 공유 DB) 안에 있고,
--   이 스크립트의 조인 기반 백필도 그 DB를 대상으로 한다.
--   나중에 실제로 member 데이터가 member-postgres로 이전되면(회원 테이블이
--   더 이상 이 DB에 없으면) 이 조인 방식은 더 이상 못 쓰게 되므로, 그 전에만
--   유효한 임시방편이라는 점을 알아둘 것.
--
-- 언제 필요한가:
--   로컬 Postgres에 이 브랜치를 받기 전에 생성된 orders 데이터가 남아있으면,
--   Hibernate ddl-auto: update 가 "ALTER TABLE orders ADD COLUMN ... NOT NULL"을
--   시도하다 Postgres가 거부해서 앱이 아예 기동하지 않는다.
--   (반대로 orders 테이블이 비어 있었다면 이미 정상적으로 컬럼이 붙어 있으니
--    이 스크립트를 실행할 필요가 없다 — 먼저 아래 0번으로 확인할 것.)
--
-- 이 문제를 해결하는 두 가지 방법:
--   A) 기존 로컬 데이터를 보존하고 싶다면 -> 이 스크립트를 앱 기동 전에 실행
--   B) 로컬 테스트 데이터라 버려도 상관없다면 -> 이 스크립트 대신 아래처럼
--      로컬 DB 볼륨을 통째로 초기화하는 게 더 빠르다.
--        docker compose down -v
--        docker compose up -d
--
-- 사용법(A를 선택한 경우):
--   앱을 끈 상태에서 이 파일 전체를 psql로 실행한다.
--     PGPASSWORD=openbake psql -h localhost -U openbake -d openbake \
--       -f scripts/migrations/2026-08-08-order-buyer-seller-snapshot-backfill.sql
--   실행 후 앱을 다시 켜면 ddl-auto: update 가 이미 존재하는 컬럼을 건드리지 않는다.
--
-- 안전성: 전부 NULL인 행만 건드리므로 여러 번 실행해도 안전하다(idempotent).
-- ============================================================================

-- 0. 사전 확인: 지금 백필이 필요한 상태인지 확인
--    (buyer_name/seller_phone_number 컬럼이 아직 없거나, NULL인 행이 있는지)
-- \d orders
-- SELECT count(*) FROM orders WHERE buyer_name IS NULL OR seller_phone_number IS NULL;

-- 1. 컬럼이 아직 없다면 nullable 상태로 먼저 추가한다.
--    (이미 NOT NULL로 존재해서 이 문장이 실패한다면 -> 2번부터 이어서 실행하면 된다)
ALTER TABLE orders ADD COLUMN IF NOT EXISTS buyer_name VARCHAR(255);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS seller_phone_number VARCHAR(255);

-- 컬럼이 이미 NOT NULL로 존재하는 경우를 대비해 우선 제약을 풀어둔다(존재하지 않으면 무시됨).
ALTER TABLE orders ALTER COLUMN buyer_name DROP NOT NULL;
ALTER TABLE orders ALTER COLUMN seller_phone_number DROP NOT NULL;

-- 2. 구매자 이름 백필 — member 테이블과 조인
UPDATE orders o
SET buyer_name = m.name
FROM members m
WHERE o.member_id = m.id
  AND o.buyer_name IS NULL;

-- 3. 판매자 연락처 백필 — seller -> member 조인
UPDATE orders o
SET seller_phone_number = m.phone_number
FROM sellers s
JOIN members m ON s.member_id = m.id
WHERE o.seller_id = s.id
  AND o.seller_phone_number IS NULL;

-- 4. 회원 탈퇴 등으로 조인이 안 되는 행에 대한 fallback
UPDATE orders SET buyer_name = '알 수 없음' WHERE buyer_name IS NULL;
UPDATE orders SET seller_phone_number = '알 수 없음' WHERE seller_phone_number IS NULL;

-- 5. 백필 완료 후 다시 NOT NULL 제약 적용
ALTER TABLE orders ALTER COLUMN buyer_name SET NOT NULL;
ALTER TABLE orders ALTER COLUMN seller_phone_number SET NOT NULL;

-- 6. 검증
-- SELECT count(*) FROM orders WHERE buyer_name IS NULL OR seller_phone_number IS NULL; -- 0 이어야 정상
