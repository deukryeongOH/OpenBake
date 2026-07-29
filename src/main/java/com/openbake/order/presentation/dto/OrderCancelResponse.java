package com.openbake.order.presentation.dto;

import com.openbake.order.domain.OrderState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

//주문 취소 응답. 전액 환불 + 재고 복구 후의 결과.
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderCancelResponse {
    @Schema(description = "취소된 주문 ID", example = "101")
    private Long orderId;

    @Schema(description = "주문 상태. 취소가 성공했으므로 항상 CANCELED.", example = "CANCELED")
    private OrderState orderState;

    @Schema(description = "환불 금액. 부분 환불은 없고 결제 금액 전액이다.", example = "24000")
    private BigDecimal refundAmount;

    @Schema(description = "환불 후 예치금 잔액", example = "100000")
    private BigDecimal balanceAfter;

    @Schema(description = "취소 시각", example = "2026-07-28T15:00:00")
    private LocalDateTime canceledAt;
}
