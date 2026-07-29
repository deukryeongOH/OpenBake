package com.openbake.cart.presentation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder

public class CartCreateRequest {
    @Schema(description = "담을 드롭 ID. 재고 선점을 통과한 드롭이어야 한다.", example = "7")
    @NotNull(message = "드롭 ID는 필수입니다.")
    private Long dropId;

    @Schema(description = "수량. 드롭에서 선점한 수량과 같은 값이다.", example = "2")
    @NotNull(message = "수량은 필수입니다.")
    @Min(value = 1, message = "수량은 1개 이상이어야 합니다.")
    private Integer quantity;
}