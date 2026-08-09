package com.openbake.cart.presentation;

import com.openbake.cart.application.CartPickupDateResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

public record CartPickupDateResponse (
    @Schema(description = "장바구니 ID", example = "31")
    Long cartId,

    @Schema(description = "저장된 픽업 날짜. 재선택하면 이 값이 덮어써진다.", example = "2026-08-01")
    LocalDate pickupDate
) {
    public static CartPickupDateResponse from(CartPickupDateResult result) {
        return new CartPickupDateResponse(
                result.cartId(),
                result.pickupDate()
        );
    }
}
