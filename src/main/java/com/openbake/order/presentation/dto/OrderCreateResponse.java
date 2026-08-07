package com.openbake.order.presentation.dto;

import com.openbake.order.application.OrderCreateResult;
import com.openbake.order.domain.OrderState;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

//주문 생성(결제) 응답.
public record OrderCreateResponse(
        @Schema(description = "생성된 주문 ID", example = "101")
        Long orderId,

        @Schema(description = "주문 상태. 결제가 성공했으므로 항상 PAID.", example = "PAID")
        OrderState orderState,

        @Schema(description = "총 결제 금액 = 주문 시점 가격 × 수량", example = "24000")
        BigDecimal totalAmount,

        @Schema(description = "결제 후 예치금 잔액", example = "76000")
        BigDecimal balanceAfter,

        @Schema(description = "결제완료 시각", example = "2026-07-28T14:05:00")
        LocalDateTime paidAt
) {

    public static OrderCreateResponse from(OrderCreateResult result) {
        return new OrderCreateResponse(
                result.orderId(),
                result.orderState(),
                result.totalAmount(),
                result.balanceAfter(),
                result.paidAt()
        );
    }
}