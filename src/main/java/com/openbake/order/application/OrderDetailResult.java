package com.openbake.order.application;

import com.openbake.order.domain.OrderState;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record OrderDetailResult(
        Long orderId,
        OrderItemInfo orderItem,
        SellerInfo seller,
        LocalDate pickupDate,
        OrderState orderState,
        LocalDateTime paidAt,
        LocalDateTime confirmedAt,
        LocalDateTime canceledAt
) {

    public record OrderItemInfo(
            Long dropId,
            String dropName,
            BigDecimal price,
            int quantity
    ) {
    }

    public record SellerInfo(
            Long sellerId,
            String sellerName,
            String address,
            String phoneNumber
    ) {
    }
}