package com.openbake.order.presentation.dto;

import com.openbake.order.application.OrderSummaryResult;
import com.openbake.order.domain.OrderState;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

//주문 목록의 한 항목. dropName·quantity 는 order_items, sellerName 은 seller 조회.
public record OrderSummaryResponse(
        @Schema(description = "주문 ID. 상세/취소/확정 호출에 쓴다.", example = "101")
        Long orderId,

        @Schema(description = "주문 시점 상품명(스냅샷)", example = "말차 크루아상")
        String dropName,

        @Schema(description = "베이커리 상호명. 조회 시점 값이며 판매자를 찾지 못하면 null.", example = "오픈베이크 연남")
        String sellerName,

        @Schema(description = "수량", example = "2")
        int quantity,

        @Schema(description = "총 결제 금액", example = "24000")
        BigDecimal totalAmount,

        @Schema(description = "주문 상태: PAID / CONFIRMED / CANCELED", example = "PAID")
        OrderState orderState,

        @Schema(description = "픽업 날짜", example = "2026-08-01")
        LocalDate pickupDate,

        @Schema(description = "결제완료 시각. 목록 정렬 기준은 주문 ID 역순이다.", example = "2026-07-28T14:05:00")
        LocalDateTime paidAt
) {

    public static OrderSummaryResponse from(OrderSummaryResult result) {
        return new OrderSummaryResponse(
                result.orderId(),
                result.dropName(),
                result.sellerName(),
                result.quantity(),
                result.totalAmount(),
                result.orderState(),
                result.pickupDate(),
                result.paidAt()
        );
    }
}
