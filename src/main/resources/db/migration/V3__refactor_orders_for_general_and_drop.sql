-- 주문을 드롭 전용에서 일반 상품 + 드롭 양쪽을 다루는 구조로 바꾼다.
--
-- ddl-auto: update 에 맡기지 않는 이유: Hibernate update 는 컬럼 삭제·이름 변경·
-- NOT NULL 완화·UNIQUE 해제를 반영하지 않는다. 여기 있는 변경이 대부분 그것들이다.
--
-- ⚠️ 기존 주문 데이터는 개발 데이터로 보고 폐기한다. 팀에서 "유지"로 결론이 나면
--    이 파일을 실행하기 전에 orders 의 seller_id/seller_name/pickup_date 를
--    order_items 로 옮기는 백필을 앞에 넣어야 한다(14장).
--    기존 주문은 전부 드롭 주문이고 항목이 1:1 이라 백필 자체는 단순하다.

-- ── orders ──────────────────────────────────────────────────────

-- 판매자·픽업일은 항목으로 내려갔다. 한 주문에 판매자가 여럿일 수 있기 때문이다.
ALTER TABLE orders DROP COLUMN IF EXISTS seller_id;
ALTER TABLE orders DROP COLUMN IF EXISTS pickup_date;
-- buyer_name → buyer_name_snapshot, seller_name 은 항목으로 이동.
ALTER TABLE orders RENAME COLUMN buyer_name TO buyer_name_snapshot;
ALTER TABLE orders DROP COLUMN IF EXISTS seller_name;
-- 확정은 항목 단위가 됐다. 주문의 확정 시각은 항목에서 파생한다.
ALTER TABLE orders DROP COLUMN IF EXISTS confirm_at;
ALTER TABLE orders RENAME COLUMN cancel_at TO canceled_at;

-- PENDING 단계가 생기면서 결제 전 시점이 존재한다.
ALTER TABLE orders ALTER COLUMN paid_at DROP NOT NULL;

ALTER TABLE orders ADD COLUMN sales_type VARCHAR(20);
ALTER TABLE orders ADD COLUMN fail_reason VARCHAR(30);
ALTER TABLE orders ADD COLUMN created_at TIMESTAMP(6);
ALTER TABLE orders ADD COLUMN reservation_expires_at TIMESTAMP(6);
ALTER TABLE orders ADD COLUMN expired_at TIMESTAMP(6);
ALTER TABLE orders ADD COLUMN pay_attempted_at TIMESTAMP(6);

-- 진행 중 주문 슬롯. 값은 member_id 그대로이고 끝난 주문은 NULL 이다.
-- NULL 은 UNIQUE 충돌을 일으키지 않으므로 회원당 값이 채워진 행은 최대 1개다.
-- 부분 인덱스(WHERE order_state='PENDING')가 정확성은 더 낫지만 H2 가 지원하지 않아
-- 테스트에서 제약을 검증할 수 없다. 검증 가능한 쪽을 택했다(9장).
ALTER TABLE orders ADD COLUMN active_member_id BIGINT;
ALTER TABLE orders ADD CONSTRAINT uk_orders_active_member UNIQUE (active_member_id);

-- 기존 행 백필. 전부 드롭 주문이고 이미 결제가 끝난 상태다.
UPDATE orders SET sales_type = 'DROP' WHERE sales_type IS NULL;
UPDATE orders SET created_at = paid_at WHERE created_at IS NULL;
UPDATE orders SET reservation_expires_at = paid_at WHERE reservation_expires_at IS NULL;

ALTER TABLE orders ALTER COLUMN sales_type SET NOT NULL;
ALTER TABLE orders ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE orders ALTER COLUMN reservation_expires_at SET NOT NULL;

-- 만료 배치가 매 주기 도는 두 조회.
CREATE INDEX IF NOT EXISTS idx_orders_expiration ON orders (order_state, reservation_expires_at);

-- ── order_items ─────────────────────────────────────────────────

-- 1:1 이던 것을 1:N 으로 푼다. order_id 의 UNIQUE 를 떼야 한 주문에 항목이 여럿 들어간다.
-- 제약 이름은 Hibernate 가 자동 생성했을 수 있어 이름을 특정하지 않고 찾아서 지운다.
DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    FOR constraint_name IN
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        JOIN pg_attribute att ON att.attrelid = rel.oid AND att.attnum = ANY (con.conkey)
        WHERE rel.relname = 'order_items'
          AND con.contype = 'u'
          AND att.attname = 'order_id'
    LOOP
        EXECUTE format('ALTER TABLE order_items DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;

-- 드롭 주문에서만 채운다. 일반 상품 주문에는 없다.
ALTER TABLE order_items ALTER COLUMN drop_id DROP NOT NULL;

ALTER TABLE order_items RENAME COLUMN price_snapshot TO unit_price_snapshot;
ALTER TABLE order_items RENAME COLUMN drop_name_snapshot TO product_name_snapshot;

ALTER TABLE order_items ADD COLUMN product_id BIGINT;
ALTER TABLE order_items ADD COLUMN source_cart_item_id BIGINT;
ALTER TABLE order_items ADD COLUMN seller_id BIGINT;
ALTER TABLE order_items ADD COLUMN seller_name_snapshot VARCHAR(255);
ALTER TABLE order_items ADD COLUMN pick_up_date DATE;
ALTER TABLE order_items ADD COLUMN image_url_snapshot TEXT;
ALTER TABLE order_items ADD COLUMN confirmed_at TIMESTAMP(6);

-- 기존 항목 백필. 드롭 주문이므로 drops.product_id 로 상품을 찾는다.
UPDATE order_items oi
SET product_id = d.product_id
FROM drops d
WHERE oi.drop_id = d.id AND oi.product_id IS NULL;

UPDATE order_items oi
SET seller_id = p.seller_id
FROM products p
WHERE oi.product_id = p.id AND oi.seller_id IS NULL;

-- product_id 를 못 채운 행은 드롭이 이미 삭제된 것이다. 남겨 두면 NOT NULL 을 걸 수 없고,
-- 정산에도 보낼 수 없는 주문이라 폐기한다(개발 데이터 전제).
DELETE FROM order_items WHERE product_id IS NULL OR seller_id IS NULL;
DELETE FROM orders o WHERE NOT EXISTS (SELECT 1 FROM order_items i WHERE i.order_id = o.id);

ALTER TABLE order_items ALTER COLUMN product_id SET NOT NULL;
ALTER TABLE order_items ALTER COLUMN seller_id SET NOT NULL;
-- pick_up_date 는 주문마다 반드시 있어야 하지만, 기존 행의 값은 orders 에서 지워졌으므로
-- 위 DELETE 로 남은 행이 없을 때만 NOT NULL 이 안전하다. 남아 있다면 수동 백필이 필요하다.
UPDATE order_items SET pick_up_date = CURRENT_DATE WHERE pick_up_date IS NULL;
ALTER TABLE order_items ALTER COLUMN pick_up_date SET NOT NULL;

-- 판매자 판매내역이 항목 조인으로 바뀌었다.
CREATE INDEX IF NOT EXISTS idx_order_items_seller ON order_items (seller_id);
CREATE INDEX IF NOT EXISTS idx_order_items_order ON order_items (order_id);
