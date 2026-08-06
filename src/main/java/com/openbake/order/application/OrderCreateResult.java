package com.openbake.order.application;

import com.openbake.order.domain.OrderState;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderCreateResult(
        Long orderId,
        OrderState orderState,
        BigDecimal totalAmount,
        BigDecimal balanceAfter,
        LocalDateTime paidAt
) {
}