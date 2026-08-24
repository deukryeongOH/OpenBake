package com.openbake.order.presentation.dto;

import com.openbake.order.application.OrderCreateResult;
import com.openbake.order.application.OrderSheetItem;
import com.openbake.order.domain.OrderState;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 생성 응답 = <b>주문서 화면 데이터</b>.
 *
 * 아직 결제 전(PENDING)이라 결제 관련 값이 없다.
 * 판매자도 담지 않는다 — 주문 진행 중에는 판매자를 노출하지 않는다.
 */
public record OrderCreateResponse(
        @Schema(description = "주문 ID", example = "101")
        Long orderId,

        @Schema(description = "주문 상태. 생성 직후는 항상 PENDING.", example = "PENDING")
        OrderState orderState,

        @Schema(description = "결제 예정 금액", example = "9000")
        BigDecimal totalAmount,

        @Schema(description = "이 시각까지 결제하지 않으면 자동 취소된다.", example = "2026-08-20T15:15:00")
        LocalDateTime reservationExpiresAt,

        @Schema(description = "주문서 항목")
        List<Item> items,

        @Schema(description = "드롭 우선권으로 자동 만료시킨 기존 진행 중 주문 ID. 없으면 null.", example = "97")
        Long yieldedOrderId
) {

    public record Item(
            Long orderItemId,
            String productName,
            String imageUrl,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal,
            LocalDate pickUpDate
    ) {
        static Item from(OrderSheetItem item) {
            return new Item(item.orderItemId(), item.productName(), item.imageUrl(),
                    item.quantity(), item.unitPrice(), item.subtotal(), item.pickUpDate());
        }
    }

    public static OrderCreateResponse from(OrderCreateResult result) {
        return new OrderCreateResponse(
                result.orderId(),
                result.orderState(),
                result.totalAmount(),
                result.reservationExpiresAt(),
                result.items().stream().map(Item::from).toList(),
                result.yieldedOrderId()
        );
    }
}
