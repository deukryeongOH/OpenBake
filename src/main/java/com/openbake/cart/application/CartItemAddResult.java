package com.openbake.cart.application;

import com.openbake.cart.domain.CartItem;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 담기·수량 변경·픽업일 변경의 공통 응답.
 * 변경된 항목 하나의 최종 상태만 돌려준다. 화면 전체는 조회 API 로 다시 그린다.
 */
public record CartItemAddResult(
        Long cartId,
        Long cartItemId,
        Long productId,
        int quantity,
        LocalDate pickUpDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static CartItemAddResult from(Long cartId, CartItem item) {
        return new CartItemAddResult(
                cartId,
                item.getCartItemId(),
                item.getProductId(),
                item.getQuantity(),
                item.getPickUpDate(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
