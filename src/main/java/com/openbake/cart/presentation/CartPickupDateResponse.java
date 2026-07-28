package com.openbake.cart.presentation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder

public class CartPickupDateResponse {
    @Schema(description = "장바구니 ID", example = "31")
    private Long cartId;

    @Schema(description = "저장된 픽업 날짜. 재선택하면 이 값이 덮어써진다.", example = "2026-08-01")
    private LocalDate pickupDate;
}
