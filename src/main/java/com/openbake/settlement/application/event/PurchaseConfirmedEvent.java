package com.openbake.settlement.application.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PurchaseConfirmedEvent(
        String eventId,
        Long orderId,
        Long orderItemId,
        Long sellerId,
        Long productId,
        String productNameSnapshot,
        Integer quantity,
        BigDecimal grossAmount,
        OffsetDateTime purchaseConfirmedAt
) {
}