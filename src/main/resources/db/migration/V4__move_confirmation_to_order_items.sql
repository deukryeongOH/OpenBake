-- 구매확정 상태를 주문 전체가 아니라 주문 항목별로 관리한다.
--
-- Order는 주문서·결제·전체 취소 상태만 관리하고, 각 상품의 구매확정 여부는
-- OrderItem이 독립적으로 가진다. 기존 CONFIRMED 주문은 모든 항목의 확정이 끝난
-- 주문이므로 항목 상태를 먼저 백필한 뒤 Order 상태를 PAID로 되돌린다.

ALTER TABLE order_items ADD COLUMN item_status VARCHAR(20);

UPDATE order_items oi
SET item_status = CASE
    WHEN oi.confirmed_at IS NOT NULL THEN 'CONFIRMED'
    WHEN o.order_state = 'CANCELED' THEN 'CANCELED'
    ELSE 'UNCONFIRMED'
END
FROM orders o
WHERE oi.order_id = o.id;

ALTER TABLE order_items ALTER COLUMN item_status SET NOT NULL;

-- 구매확정은 Order 상태가 아니다. 기존 확정 주문도 결제가 완료된 주문이라는 의미의
-- PAID로 통합하고, 상품별 확정 여부는 위에서 채운 item_status로 조회한다.
UPDATE orders
SET order_state = 'PAID'
WHERE order_state = 'CONFIRMED';

-- 자동 구매확정 배치는 미확정 항목을 직접 찾는다.
CREATE INDEX IF NOT EXISTS idx_order_items_confirmation
    ON order_items (item_status, order_id);
