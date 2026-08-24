package com.openbake.order.presentation.dto;

import com.openbake.order.application.OrderPayResult;
import com.openbake.order.domain.OrderState;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 결제 응답.
 *
 * outcome 을 따로 내리는 이유는 <b>타임아웃이 실패가 아니기 때문</b>이다.
 * PROCESSING 을 받으면 프론트는 "결제 또는 환불 결과를 확인 중입니다"를 표시한다.
 * Order는 별도 결제 진행 상태를 저장해 재결제·취소·만료를 차단하지 않는다.
 */
public record OrderPayResponse(
        @Schema(description = "주문 ID", example = "101")
        Long orderId,

        @Schema(description = "주문 상태", example = "PAID")
        OrderState orderState,

        @Schema(description = "PAID / PAYMENT_FAILED / OUT_OF_STOCK / PAYMENT_REVERSED / PROCESSING", example = "PAID")
        OrderPayResult.Outcome outcome,

        @Schema(description = "결제 금액", example = "9000")
        BigDecimal totalAmount,

        @Schema(description = "결제 시도 후 예치금 잔액. 성공·확정 실패 시 채워질 수 있다.", example = "41000")
        BigDecimal balanceAfter,

        @Schema(description = "결제 완료 시각. 결제 성공 시에만 채워진다.")
        LocalDateTime paidAt,

        @Schema(description = "결제 실패·보상 결과 표시 문구", example = "예치금 잔액이 부족합니다.")
        String message
) {

    public static OrderPayResponse from(OrderPayResult result) {
        return new OrderPayResponse(
                result.orderId(),
                result.orderState(),
                result.outcome(),
                result.totalAmount(),
                result.balanceAfter(),
                result.paidAt(),
                result.message()
        );
    }
}
