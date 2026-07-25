package com.openbake.order.presentation.dto;

import com.openbake.order.domain.OrderState;
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
    private Long orderId;
    private OrderState orderState;
    private BigDecimal totalAmount;
    private BigDecimal balanceAfter;   //결제 후 예치금 잔액
    private LocalDateTime paidAt;
}
