package com.openbake.cart.application;

/**
 * 장바구니 항목을 지금 주문할 수 있는지.
 *
 * 장바구니는 재고를 선점하지 않으므로 담아둔 뒤 상품이 사라지거나 재고가 줄 수 있다.
 * 조회할 때마다 판정해서 내려주고, 프론트가 해당 항목과 주문 버튼을 비활성 처리한다.
 */
public enum CartItemStatus {
    ORDERABLE,
    //상품이 삭제됐다. 다시 담아야 한다.
    PRODUCT_DELETED,
    //재고가 0이다.
    SOLD_OUT,
    //재고가 담아둔 수량보다 적다. 수량을 줄이면 주문할 수 있다.
    INSUFFICIENT_STOCK,
    //고른 픽업 날짜가 더 이상 선택 가능일이 아니다(판매자가 지웠거나 날짜가 지났다).
    //선택 가능 목록은 최신으로 함께 내려가므로 다시 고르면 주문할 수 있다.
    PICKUP_DATE_UNAVAILABLE
}
