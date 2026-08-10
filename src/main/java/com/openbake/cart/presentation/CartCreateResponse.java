package com.openbake.cart.presentation;

import com.openbake.cart.application.CartCreateResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record CartCreateResponse (
        @Schema(description = "생성된 장바구니 ID", example = "31")
        Long cartId,

        @Schema(description = "담긴 드롭 ID", example = "7")
        Long dropId,

        @Schema(description = "수량", example = "2")
        int quantity,

        @Schema(description = "만료 시각. 지나면 선점 재고가 회수되고 다시 담아야 한다.", example = "2026-07-28T14:15:00")
        LocalDateTime expiresAt,

        @Schema(description = "생성 시각", example = "2026-07-28T14:00:00")
        LocalDateTime createdAt
) {

    public static CartCreateResponse from (CartCreateResult result) {
        return new CartCreateResponse(
                result.cartId(),
                result.dropId(),
                result.quantity(),
                result.expiresAt(),
                result.createdAt()
        );
    }
}