package com.openbake.cart.presentation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CartItemQuantityRequest {

    @Schema(description = "바꿀 수량. 더하는 게 아니라 이 값으로 교체한다.", example = "3")
    @Min(value = 1, message = "수량은 1개 이상이어야 합니다.")
    private int quantity;
}
