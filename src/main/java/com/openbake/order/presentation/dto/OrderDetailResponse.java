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
 * 주문 상세. cancelable 은 서버가 계산해 내려준다(클라이언트가 판단하지 않는다).
 * dropCloseAt 은 저장값이 아니라 조회 시점에 드롭에서 읽은 값이다.
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
    private LocalDateTime dropCloseAt;
    private boolean cancelable;
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
