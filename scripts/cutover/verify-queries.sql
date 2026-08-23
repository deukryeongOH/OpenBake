-- =============================================================================
-- Cutover 데이터 검증 쿼리
-- 설계 근거: docs/k3s-learning/12-compose-to-k3s-cutover-rollback-plan.md 5장
--
-- Compose와 k3s 양쪽에서 동일하게 실행해 결과를 나란히 비교한다.
--   psql -h <host> -p <port> -U <user> -d <db> -f verify-queries.sql
--
-- 이 파일은 판정하지 않는다. 모든 결과를 출력만 하고, 값을 비교해 Go/No-Go를
-- 정하는 것은 사람이다.
--
-- 핵심 테이블 범위 (정합성이 무너지면 서비스가 죽는 것 우선):
--   members, products, drops, orders, order_items, order_payments,
--   payment_records, settlements
-- 근거: src/main/java 및 member-service/payment-service의 @Entity·@Table 확인.
-- DB가 core/member/payment/ai로 분리되어 있어 한 DB에는 이 중 일부만 존재한다 —
-- to_regclass로 존재하는 테이블만 걸러서 검증한다.
-- =============================================================================

\echo '=== 0. 연결 대상 ==='
SELECT current_database() AS db, version();

-- -----------------------------------------------------------------------------
-- 1. 구조 — PostgreSQL version, extension, table/index/constraint 수
-- -----------------------------------------------------------------------------
\echo '=== 1. 구조 — extension ==='
SELECT extname, extversion FROM pg_extension ORDER BY extname;

\echo '=== 1. 구조 — table / index / constraint 수 ==='
SELECT
  (SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public') AS table_count,
  (SELECT count(*) FROM pg_indexes WHERE schemaname = 'public') AS index_count,
  (SELECT count(*) FROM information_schema.table_constraints WHERE table_schema = 'public') AS constraint_count;

-- -----------------------------------------------------------------------------
-- 2. 데이터 — 핵심 테이블 정확 row count
-- -----------------------------------------------------------------------------
\echo '=== 2. 데이터 — 핵심 테이블 row count (이 DB에 존재하는 테이블만 출력됨) ==='
DO $$
DECLARE
  t text;
  cnt bigint;
BEGIN
  FOREACH t IN ARRAY ARRAY['members','products','drops','orders','order_items',
                            'order_payments','payment_records','settlements']
  LOOP
    IF to_regclass('public.' || t) IS NOT NULL THEN
      EXECUTE format('SELECT count(*) FROM %I', t) INTO cnt;
      RAISE NOTICE '% : % rows', rpad(t, 20), cnt;
    END IF;
  END LOOP;
END $$;

\echo '=== 2. 데이터 — 상태별 aggregate (해당 테이블이 있는 DB에서만 출력된다) ==='
-- orders.order_state: PAID / CONFIRMED / CANCELED (src/main/java/com/openbake/order/domain/OrderState.java)
-- order_payments.status: PAID / CONFIRMED / REFUNDED (payment-service PaymentStatus)
-- to_regclass로 존재 여부를 먼저 확인한 뒤 동적 SQL로 실행한다 —
-- 존재하지 않는 테이블을 정적으로 참조하면 파싱 단계에서 바로 에러가 난다.
DO $$
DECLARE
  r record;
BEGIN
  IF to_regclass('public.orders') IS NOT NULL THEN
    FOR r IN EXECUTE 'SELECT order_state::text AS value, count(*) AS cnt FROM orders GROUP BY order_state ORDER BY 1' LOOP
      RAISE NOTICE 'orders.order_state = % : % rows', r.value, r.cnt;
    END LOOP;
  END IF;

  IF to_regclass('public.order_payments') IS NOT NULL THEN
    FOR r IN EXECUTE 'SELECT status::text AS value, count(*) AS cnt FROM order_payments GROUP BY status ORDER BY 1' LOOP
      RAISE NOTICE 'order_payments.status = % : % rows', r.value, r.cnt;
    END LOOP;
  END IF;

  IF to_regclass('public.settlements') IS NOT NULL THEN
    FOR r IN EXECUTE 'SELECT status::text AS value, count(*) AS cnt FROM settlements GROUP BY status ORDER BY 1' LOOP
      RAISE NOTICE 'settlements.status = % : % rows', r.value, r.cnt;
    END LOOP;
  END IF;
END $$;

-- -----------------------------------------------------------------------------
-- 3. ID — sequence 현재값이 해당 table 최대 ID 이상인지
--    IDENTITY 컬럼(GenerationType.IDENTITY)의 실제 시퀀스 이름을 하드코딩하지 않고
--    pg_get_serial_sequence로 조회한다. 어긋나면 신규 insert가 기존 PK와 충돌한다.
-- -----------------------------------------------------------------------------
\echo '=== 3. ID — sequence last_value >= max(id) ? (모두 ok=t 여야 한다) ==='
DO $$
DECLARE
  t text;
  seq regclass;
  seq_val bigint;
  max_id bigint;
BEGIN
  FOREACH t IN ARRAY ARRAY['members','products','drops','orders','order_items',
                            'order_payments','payment_records','settlements']
  LOOP
    IF to_regclass('public.' || t) IS NOT NULL THEN
      seq := pg_get_serial_sequence('public.' || t, 'id');
      IF seq IS NOT NULL THEN
        EXECUTE format('SELECT last_value FROM %s', seq) INTO seq_val;
        EXECUTE format('SELECT COALESCE(MAX(id), 0) FROM %I', t) INTO max_id;
        RAISE NOTICE '% : sequence_last=% max_id=% ok=%',
          rpad(t, 20), seq_val, max_id, (seq_val >= max_id);
      ELSE
        RAISE NOTICE '% : id 컬럼에 연결된 시퀀스를 찾지 못함 (id 컬럼이 없거나 IDENTITY가 아님)', rpad(t, 20);
      END IF;
    END IF;
  END LOOP;
END $$;

-- -----------------------------------------------------------------------------
-- 4. 관계 — FK와 주요 참조 데이터 유효성
-- -----------------------------------------------------------------------------
\echo '=== 4. 관계 — 같은 DB 내부 FK 위반 건수 (0이어야 한다, core DB에서만 값이 나온다) ==='
-- order_items.order_id → orders.id (core DB 안의 실제 FK)
DO $$
DECLARE
  violation_count bigint;
BEGIN
  IF to_regclass('public.order_items') IS NOT NULL AND to_regclass('public.orders') IS NOT NULL THEN
    EXECUTE 'SELECT count(*) FROM order_items oi LEFT JOIN orders o ON oi.order_id = o.id WHERE o.id IS NULL'
      INTO violation_count;
    RAISE NOTICE 'order_items -> orders violation_count = %', violation_count;
  END IF;
END $$;

\echo '=== 4. 관계 — 서비스 간(cross-DB) 참조는 SQL JOIN으로 검증할 수 없다 ==='
\echo '핵심 테이블이 서로 다른 물리 DB(core/member/payment)에 나뉘어 있어 아래는 각 DB에서'
\echo '따로 실행한 뒤 사람이 건수를 맞대어 비교한다:'
\echo '  payment DB: SELECT count(DISTINCT order_id) FROM order_payments;'
\echo '  core DB   : SELECT count(*) FROM orders;'
\echo '  (OrderPayment 클래스 주석: 주문 1건당 1개 — 두 값이 같아야 한다)'
