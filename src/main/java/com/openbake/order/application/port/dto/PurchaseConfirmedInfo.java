package com.openbake.order.application.port.dto;

import java.math.BigDecimal;

import java.time.OffsetDateTime;

public record PurchaseConfirmedInfo(
        String eventId,
        Long orderId,
        Long orderItemId,
        Long sellerId,
        Long dropId,
        String productNameSnapshot,
        Integer quantity,
        BigDecimal grossAmount,
        OffsetDateTime purchaseConfirmedAt
) {
}