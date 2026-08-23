package com.openbake.order.presentation.dto;

import com.openbake.order.application.SellerOrderSummaryResult;
import com.openbake.order.domain.OrderItemStatus;
import com.openbake.order.domain.OrderState;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 판매자 판매내역 한 줄.
 *
 * <b>자기 항목만 담기고 금액도 자기 몫 소계다.</b> 한 주문에 판매자가 여럿일 수 있어
 * 주문 전체 금액을 보여주면 남의 매출이 자기 화면에 뜬다.
 */
public record SellerOrderSummaryResponse(
        Long orderId,
        String buyerName,
        OrderState orderState,

        @Schema(description = "자기 항목들의 소계 합. 주문 전체 금액이 아니다.", example = "5000")
        BigDecimal sellerAmount,

        LocalDateTime paidAt,
        LocalDateTime canceledAt,
        List<Item> items
) {

    public record Item(
            Long orderItemId,
            Long productId,
            Long dropId,
            String productName,
            int quantity,
            BigDecimal subtotal,
            LocalDate pickUpDate,
            @Schema(description = "항목별 구매확정 상태", example = "UNCONFIRMED")
            OrderItemStatus itemStatus,
            @Schema(description = "확정 시각. null 이면 아직 확정 버튼을 누르지 않았다.")
            LocalDateTime confirmedAt
    ) {
    }

    public static SellerOrderSummaryResponse from(SellerOrderSummaryResult result) {
        return new SellerOrderSummaryResponse(
                result.orderId(),
                result.buyerName(),
                result.orderState(),
                result.sellerAmount(),
                result.paidAt(),
                result.canceledAt(),
                result.items().stream()
                        .map(item -> new Item(
                                item.orderItemId(),
                                item.productId(),
                                item.dropId(),
                                item.productName(),
                                item.quantity(),
                                item.subtotal(),
                                item.pickUpDate(),
                                item.itemStatus(),
                                item.confirmedAt()))
                        .toList()
        );
    }
}
