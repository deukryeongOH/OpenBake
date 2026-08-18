package com.openbake.cart.presentation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CartItemAddRequest {

    @Schema(description = "담을 일반 상품 ID", example = "7")
    @NotNull(message = "상품 ID는 필수입니다.")
    private Long productId;

    @Schema(description = "수량. 이미 담긴 상품이면, 기존 수량에 더해진다.", example = "2")
    @Min(value = 1, message = "수량은 1개 이상이어야 합니다.")
    private int quantity;

    @Schema(description = "픽업 날짜. 담을 때는 선택하지 않아도 되며, 주문으로 넘어갈 때 필수다.", example = "2026-08-20")
    private LocalDate pickUpDate;
}
