package com.openbake.order.presentation.dto;

import com.openbake.order.application.OrderCancelResult;
import com.openbake.order.domain.OrderState;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

//주문 취소 응답. 전액 환불 + 재고 복구 후의 결과.
public record OrderCancelResponse(
        @Schema(description = "취소된 주문 ID", example = "101")
        Long orderId,

        @Schema(description = "주문 상태. 취소가 성공했으므로 항상 CANCELED.", example = "CANCELED")
        OrderState orderState,

        @Schema(description = "환불 금액. 부분 환불은 없고 결제 금액 전액이다.", example = "24000")
        BigDecimal refundAmount,

        @Schema(description = "환불 후 예치금 잔액", example = "100000")
        BigDecimal balanceAfter,

        @Schema(description = "취소 시각", example = "2026-07-28T15:00:00")
        LocalDateTime canceledAt
) {

    public static OrderCancelResponse from(OrderCancelResult result) {
        return new OrderCancelResponse(
                result.orderId(),
                result.orderState(),
                result.refundAmount(),
                result.balanceAfter(),
                result.canceledAt()
        );
    }
}