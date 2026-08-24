package com.openbake.order.application.port.dto;

import java.time.LocalDate;

/**
 * 주문 대상으로 고른 장바구니 항목. '무엇을 몇 개, 언제 픽업'만 담는다.
 *
 * 가격·판매자·상품명은 넘어오지 않는다. product 소유의 값이라 order 가 자기 포트로
 * 직접 읽어 주문 시점에 스냅샷한다. cart 가 중계하면 product 의 중계자가 된다.
 */
public record CartItemInfo(
        Long cartItemId,
        Long productId,
        int quantity,
        LocalDate pickUpDate
) {
}
