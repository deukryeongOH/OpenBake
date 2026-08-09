package com.openbake.cart.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CartDetailResult(
        Long cartId,
        DropInfo drop,
        SellerInfo seller,
        int quantity,
        BigDecimal estimatedAmount,
        List<LocalDate> pickupDates,
        LocalDate selectedPickupDate,
        LocalDateTime expiresAt,
        int remainingSeconds
) {

    public record DropInfo(
            Long dropId,
            String dropName,
            BigDecimal price,
            String imageUrl
    ) {
    }

    public record SellerInfo(
            Long sellerId,
            String sellerName
    ) {
    }
}
