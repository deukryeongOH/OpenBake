package com.openbake.order.application;

import com.openbake.order.domain.OrderState;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record OrderSummaryResult(
        Long orderId,
        String dropName,
        String sellerName,
        int quantity,
        BigDecimal totalAmount,
        OrderState orderState,
        LocalDate pickupDate,
        LocalDateTime paidAt
) {
}