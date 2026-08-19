package com.openbake.cart.application;

import com.openbake.cart.domain.CartItem;

import java.time.LocalDate;

/**
 * 주문으로 넘길 장바구니 항목. order 가 고른 항목의 '무엇을 몇 개, 언제 픽업'만 담는다.
 *
 * 가격·판매자·상품명·재고는 넣지 않는다. product 소유의 값이라 order 가 자기 포트로 직접 읽어
 * 주문 시점에 스냅샷하고 재검증해야 한다. cart 가 중계하면 product 의 중계자가 되어 책임이 번진다.
 *
 * 조회 응답(CartDetailResult)과 달리 orderable 같은 화면용 판정을 담지 않는다.
 * 장바구니의 판정은 안내용이고 주문 승인의 근거는 order 가 주문 시점에 다시 만든다.
 */
public record CartOrderItem(
        Long cartItemId,
        Long productId,
        int quantity,
        LocalDate pickUpDate
) {

    static CartOrderItem from(CartItem item) {
        return new CartOrderItem(
                item.getCartItemId(),
                item.getProductId(),
                item.getQuantity(),
                item.getPickUpDate()
        );
    }
}
