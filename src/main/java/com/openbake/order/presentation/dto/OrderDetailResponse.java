package com.openbake.order.presentation.dto;

import com.openbake.order.domain.OrderState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 주문 상세.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderDetailResponse {
    private Long orderId;
    private OrderItemInfo orderItem;
    private SellerInfo seller;
    private LocalDate pickupDate;
    private OrderState orderState;
    private LocalDateTime paidAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime canceledAt;

    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OrderItemInfo {
        private Long dropId;
        private String dropName;
        private BigDecimal price;
        private int quantity;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SellerInfo {
        private Long sellerId;
        private String sellerName;
    }
}
