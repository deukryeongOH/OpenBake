package com.openbake.order.presentation.dto;

import com.openbake.order.domain.OrderState;
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
    private Long orderId;
    private OrderState orderState;
    private BigDecimal refundAmount;
    private BigDecimal balanceAfter;
    private LocalDateTime canceledAt;
}
