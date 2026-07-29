package com.openbake.cart.presentation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder

public class CartCreateResponse {
    @Schema(description = "생성된 장바구니 ID", example = "31")
    private Long cartId;

    @Schema(description = "담긴 드롭 ID", example = "7")
    private Long dropId;

    @Schema(description = "수량", example = "2")
    private Integer quantity;

    @Schema(description = "만료 시각. 지나면 선점 재고가 회수되고 다시 담아야 한다.", example = "2026-07-28T14:15:00")
    private LocalDateTime expiresAt;

    @Schema(description = "생성 시각", example = "2026-07-28T14:00:00")
    private LocalDateTime createdAt;
}
