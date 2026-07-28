package com.openbake.order.presentation.dto;

import com.openbake.order.domain.OrderState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

//주문 생성(결제) 응답.
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderCreateResponse {
    @Schema(description = "생성된 주문 ID", example = "101")
    private Long orderId;

    @Schema(description = "주문 상태. 결제가 성공했으므로 항상 PAID.", example = "PAID")
    private OrderState orderState;

    @Schema(description = "총 결제 금액 = 주문 시점 가격 × 수량", example = "24000")
    private BigDecimal totalAmount;

    @Schema(description = "결제 후 예치금 잔액", example = "76000")
    private BigDecimal balanceAfter;

    @Schema(description = "결제완료 시각", example = "2026-07-28T14:05:00")
    private LocalDateTime paidAt;
}
