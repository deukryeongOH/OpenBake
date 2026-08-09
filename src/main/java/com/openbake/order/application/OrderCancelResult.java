package com.openbake.order.application;

import com.openbake.order.domain.Order;
import com.openbake.order.domain.OrderState;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderCancelResult (
        Long orderId,
        OrderState orderState,
        BigDecimal refundAmount,
        BigDecimal balanceAfter,
        LocalDateTime canceledAt
){
}