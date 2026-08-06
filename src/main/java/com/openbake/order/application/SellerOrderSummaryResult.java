package com.openbake.order.application;

import com.openbake.order.domain.OrderState;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SellerOrderSummaryResult(
        Long orderId,
        Long dropId,
        String dropName,
        String buyerName,
        int quantity,
        BigDecimal totalAmount,
        OrderState orderState,
        LocalDate pickupDate,
        LocalDateTime paidAt,
        LocalDateTime confirmedAt,
        LocalDateTime canceledAt
) {
}