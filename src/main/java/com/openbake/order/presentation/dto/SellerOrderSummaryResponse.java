package com.openbake.order.presentation.dto;

import com.openbake.order.domain.OrderState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

//판매자 판매내역 목록의 한 항목. dropId·dropName·quantity 는 order_items, buyerName 은 member 조회.
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SellerOrderSummaryResponse {
    private Long orderId;
    private Long dropId;
    private String dropName;
    private String buyerName;
    private int quantity;
    private BigDecimal totalAmount;
    private OrderState orderState;
    private LocalDate pickupDate;
    private LocalDateTime paidAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime canceledAt;
}
