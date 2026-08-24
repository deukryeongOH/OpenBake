package com.openbake.order.domain;

/**
 * 주문 항목의 구매확정 상태.
 *
 * <p>결제·만료·실패는 주문 전체 상태({@link OrderState})가 관리하고,
 * 상품별 구매확정과 취소 여부만 항목에 둔다. 하나의 주문에 여러 상품이 들어갈 수 있어
 * 한 항목의 확정이 다른 항목이나 주문 전체를 확정시키면 안 된다.</p>
 */
public enum OrderItemStatus {
    UNCONFIRMED,
    CONFIRMED,
    CANCELED
}
