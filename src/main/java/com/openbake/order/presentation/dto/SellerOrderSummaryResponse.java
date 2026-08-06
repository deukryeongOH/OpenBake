package com.openbake.order.presentation.dto;

import com.openbake.order.application.SellerOrderSummaryResult;
import com.openbake.order.domain.OrderState;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

//판매자 판매내역 목록의 한 항목. dropId·dropName·quantity 는 order_items, buyerName 은 member 조회.
public record SellerOrderSummaryResponse(
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

    public static SellerOrderSummaryResponse from(SellerOrderSummaryResult result) {
        return new SellerOrderSummaryResponse(
                result.orderId(),
                result.dropId(),
                result.dropName(),
                result.buyerName(),
                result.quantity(),
                result.totalAmount(),
                result.orderState(),
                result.pickupDate(),
                result.paidAt(),
                result.confirmedAt(),
                result.canceledAt()
        );
    }
}