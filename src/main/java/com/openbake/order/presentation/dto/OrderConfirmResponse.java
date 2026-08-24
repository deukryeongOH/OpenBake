package com.openbake.order.presentation.dto;

import com.openbake.order.application.OrderConfirmResult;
import com.openbake.order.domain.OrderItemStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

//구매확정 응답. 항목 단위다.
public record OrderConfirmResponse(
        @Schema(description = "주문 ID", example = "101")
        Long orderId,

        @Schema(description = "확정한 주문 항목 ID", example = "205")
        Long orderItemId,

        @Schema(description = "확정한 주문 항목의 상태", example = "CONFIRMED")
        OrderItemStatus itemStatus,

        @Schema(description = "이 항목의 확정 시각")
        LocalDateTime confirmedAt
) {

    public static OrderConfirmResponse from(OrderConfirmResult result) {
        return new OrderConfirmResponse(
                result.orderId(),
                result.orderItemId(),
                result.itemStatus(),
                result.confirmedAt()
        );
    }
}
