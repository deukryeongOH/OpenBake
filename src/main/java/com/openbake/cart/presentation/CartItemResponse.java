package com.openbake.cart.presentation;

import com.openbake.cart.application.CartItemAddResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CartItemResponse(
        @Schema(description = "장바구니 ID", example = "31")
        Long cartId,

        @Schema(description = "장바구니 항목 ID", example = "104")
        Long cartItemId,

        @Schema(description = "상품 ID", example = "7")
        Long productId,

        @Schema(description = "최종 수량. 같은 상품을 또 담았다면 합산된 값이다.", example = "5")
        int quantity,

        @Schema(description = "선택된 픽업 날짜. 아직 고르지 않았으면 null.", example = "2026-08-20")
        LocalDate pickUpDate,

        @Schema(description = "담은 시각", example = "2026-08-13T14:00:00")
        LocalDateTime createdAt,

        @Schema(description = "마지막 변경 시각", example = "2026-08-13T14:20:00")
        LocalDateTime updatedAt
) {

    public static CartItemResponse from(CartItemAddResult result) {
        return new CartItemResponse(
                result.cartId(),
                result.cartItemId(),
                result.productId(),
                result.quantity(),
                result.pickUpDate(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}
