package com.openbake.order.domain;

//주문 상태. 결제완료 → (구매확정 | 취소)
public enum OrderState {
    PAID, CONFIRMED, CANCELED
}
