package com.openbake.settlement.application.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PurchaseConfirmedEvent(
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