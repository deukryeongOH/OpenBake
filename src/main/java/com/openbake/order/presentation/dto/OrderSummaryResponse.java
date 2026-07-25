package com.openbake.order.presentation.dto;

import com.openbake.order.domain.OrderState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

//주문 목록의 한 항목. dropName·quantity 는 order_items, sellerName 은 seller 조회.
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderSummaryResponse {
    private Long orderId;
    private String dropName;
    private String sellerName;
    private int quantity;
    private BigDecimal totalAmount;
    private OrderState orderState;
    private LocalDate pickupDate;
    private LocalDateTime paidAt;
}
