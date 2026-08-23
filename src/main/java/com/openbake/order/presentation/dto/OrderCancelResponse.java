package com.openbake.order.presentation.dto;

import com.openbake.order.application.OrderCancelResult;
import com.openbake.order.domain.OrderState;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 취소 응답.
 *
 * orderState 가 EXPIRED 면 결제 전 취소라 환불이 없었다는 뜻이고(refundAmount = 0),
 * CANCELED 면 전액 환불된 것이다.
 */
public record OrderCancelResponse(
        @Schema(description = "주문 ID", example = "101")
        Long orderId,

        @Schema(description = "EXPIRED(결제 전 취소) 또는 CANCELED(결제 후 취소)", example = "CANCELED")
        OrderState orderState,

        @Schema(description = "환불 금액. 결제 전 취소면 0.", example = "9000")
        BigDecimal refundAmount,

        @Schema(description = "환불 후 예치금 잔액. 환불이 있었을 때만 채워진다.", example = "50000")
        BigDecimal balanceAfter,

        @Schema(description = "종료 시각")
        LocalDateTime endedAt
) {

    public static OrderCancelResponse from(OrderCancelResult result) {
        return new OrderCancelResponse(
                result.orderId(),
                result.orderState(),
                result.refundAmount(),
                result.balanceAfter(),
                result.endedAt()
        );
    }
}
