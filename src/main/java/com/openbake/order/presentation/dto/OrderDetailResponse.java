package com.openbake.order.presentation.dto;

import com.openbake.order.domain.OrderState;
import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(description = "주문 ID", example = "101")
    private Long orderId;

    @Schema(description = "주문 항목. 상품명/가격은 주문 시점 스냅샷이다.")
    private OrderItemInfo orderItem;

    @Schema(description = "판매자 정보")
    private SellerInfo seller;

    @Schema(description = "구매자가 선택한 픽업 날짜", example = "2026-08-01")
    private LocalDate pickupDate;

    @Schema(description = "주문 상태: PAID / CONFIRMED / CANCELED", example = "PAID")
    private OrderState orderState;

    @Schema(description = "결제완료 시각", example = "2026-07-28T14:05:00")
    private LocalDateTime paidAt;

    @Schema(description = "구매확정 시각. 확정 전이면 null.", example = "2026-08-01T18:30:00")
    private LocalDateTime confirmedAt;

    @Schema(description = "취소 시각. 취소되지 않았으면 null.", example = "2026-07-28T15:00:00")
    private LocalDateTime canceledAt;

    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OrderItemInfo {
        @Schema(description = "드롭 ID", example = "7")
        private Long dropId;

        @Schema(description = "주문 시점 상품명(스냅샷). 이후 드롭이 수정돼도 바뀌지 않는다.", example = "말차 크루아상")
        private String dropName;

        @Schema(description = "주문 시점 단가(스냅샷)", example = "12000")
        private BigDecimal price;

        @Schema(description = "수량", example = "2")
        private int quantity;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SellerInfo {
        @Schema(description = "판매자 ID", example = "3")
        private Long sellerId;

        @Schema(description = "베이커리 상호명. 조회 시점 값이며 판매자를 찾지 못하면 null.", example = "오픈베이크 연남")
        private String sellerName;
        private String address;
        private String phoneNumber;
    }
}
