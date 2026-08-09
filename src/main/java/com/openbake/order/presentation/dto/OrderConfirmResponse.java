package com.openbake.order.presentation.dto;

import com.openbake.order.application.OrderConfirmResult;
import com.openbake.order.domain.OrderState;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

//구매확정 응답(판매자).
public record OrderConfirmResponse (
        @Schema(description = "확정된 주문 ID", example = "101")
        Long orderId,

        @Schema(description = "주문 상태. 확정이 성공했으므로 항상 CONFIRMED.", example = "CONFIRMED")
        OrderState orderState,

        @Schema(description = "구매확정 시각.", example = "2026-08-01T18:30:00")
        LocalDateTime confirmedAt
){
    public static OrderConfirmResponse from(OrderConfirmResult result) {
        return new OrderConfirmResponse(
                result.orderId(),
                result.orderState(),
                result.confirmedAt()
        );
    }
}
