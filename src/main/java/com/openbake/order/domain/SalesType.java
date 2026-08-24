package com.openbake.order.domain;

/**
 * 주문 판매 형태.
 *
 * 경로가 갈려 있어 한 주문에 둘이 섞이는 경우가 로직상 없다(드롭은 장바구니를 거치지 않는다).
 * 그래서 항목이 아니라 주문에 둔다.
 *
 * 재고를 누가 언제 깎았는지가 이 값으로 갈린다.
 * GENERAL 은 order 가 결제 성공 직후 차감하고, DROP 은 lock-start 에서 drop 이 이미 깎았다.
 */
public enum SalesType {
    GENERAL, DROP
}
