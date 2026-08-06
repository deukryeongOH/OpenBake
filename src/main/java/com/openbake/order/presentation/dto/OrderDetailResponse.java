package com.openbake.order.presentation.dto;

import com.openbake.order.application.OrderDetailResult;
import com.openbake.order.domain.OrderState;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 주문 상세.
 */
public record OrderDetailResponse(
        @Schema(description = "주문 ID", example = "101")
        Long orderId,

        @Schema(description = "주문 항목. 상품명/가격은 주문 시점 스냅샷이다.")
        OrderItemInfo orderItem,

        @Schema(description = "판매자 정보")
        SellerInfo seller,

        @Schema(description = "구매자가 선택한 픽업 날짜", example = "2026-08-01")
        LocalDate pickupDate,

        @Schema(description = "주문 상태: PAID / CONFIRMED / CANCELED", example = "PAID")
        OrderState orderState,

        @Schema(description = "결제완료 시각", example = "2026-07-28T14:05:00")
        LocalDateTime paidAt,

        @Schema(description = "구매확정 시각. 확정 전이면 null.", example = "2026-08-01T18:30:00")
        LocalDateTime confirmedAt,

        @Schema(description = "취소 시각. 취소되지 않았으면 null.", example = "2026-07-28T15:00:00")
        LocalDateTime canceledAt
) {

    public static OrderDetailResponse from(OrderDetailResult result) {
        return new OrderDetailResponse(
                result.orderId(),
                OrderItemInfo.from(result.orderItem()),
                SellerInfo.from(result.seller()),
                result.pickupDate(),
                result.orderState(),
                result.paidAt(),
                result.confirmedAt(),
                result.canceledAt()
        );
    }

    public record OrderItemInfo(
            @Schema(description = "드롭 ID", example = "7")
            Long dropId,

            @Schema(description = "주문 시점 상품명(스냅샷). 이후 드롭이 수정돼도 바뀌지 않는다.", example = "말차 크루아상")
            String dropName,

            @Schema(description = "주문 시점 단가(스냅샷)", example = "12000")
            BigDecimal price,

            @Schema(description = "수량", example = "2")
            int quantity
    ) {
        public static OrderItemInfo from(OrderDetailResult.OrderItemInfo item) {
            return new OrderItemInfo(
                    item.dropId(),
                    item.dropName(),
                    item.price(),
                    item.quantity()
            );
        }
    }

    public record SellerInfo(
            @Schema(description = "판매자 ID", example = "3")
            Long sellerId,

            @Schema(description = "베이커리 상호명. 조회 시점 값이며 판매자를 찾지 못하면 null.", example = "오픈베이크 연남")
            String sellerName,

            String address,

            String phoneNumber
    ) {
        public static SellerInfo from(OrderDetailResult.SellerInfo seller) {
            return new SellerInfo(
                    seller.sellerId(),
                    seller.sellerName(),
                    seller.address(),
                    seller.phoneNumber()
            );
        }
    }
}