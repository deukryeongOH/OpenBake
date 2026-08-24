package com.openbake.order.presentation.dto;

import com.openbake.order.application.OrderSummaryResult;
import com.openbake.order.domain.OrderState;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 주문 목록 한 줄.
 *
 * 항목이 여럿이라 "소금빵 외 2건"처럼 줄여 보여준다.
 * otherItemCount 가 0 이면 단일 항목 주문이다.
 */
public record OrderSummaryResponse(
        Long orderId,

        @Schema(description = "대표 상품명(첫 항목)", example = "소금빵")
        String representativeProductName,

        @Schema(description = "대표 상품을 뺀 나머지 항목 수. 0이면 단일 항목 주문.", example = "2")
        int otherItemCount,

        @Schema(description = "대표 항목의 판매자 상호명(주문 시점 스냅샷)", example = "오픈베이크 성수점")
        String representativeSellerName,

        @Schema(description = "모든 항목의 수량 합", example = "5")
        int totalQuantity,

        BigDecimal totalAmount,
        OrderState orderState,

        @Schema(description = "가장 이른 픽업 날짜. 항목마다 다를 수 있다.")
        LocalDate nearestPickUpDate,

        LocalDateTime paidAt
) {

    public static OrderSummaryResponse from(OrderSummaryResult result) {
        return new OrderSummaryResponse(
                result.orderId(),
                result.representativeProductName(),
                result.otherItemCount(),
                result.representativeSellerName(),
                result.totalQuantity(),
                result.totalAmount(),
                result.orderState(),
                result.nearestPickUpDate(),
                result.paidAt()
        );
    }
}
