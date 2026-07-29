package com.openbake.cart.presentation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder

public class CartDetailResponse {
    @Schema(description = "장바구니 ID", example = "31")
    private Long cartId;

    @Schema(description = "담긴 드롭 정보. 조회 시점의 드롭 최신값이다(스냅샷 아님).")
    private DropInfo drop;

    @Schema(description = "판매자 정보")
    private SellerInfo seller;

    @Schema(description = "수량", example = "2")
    private Integer quantity;

    @Schema(description = "예상 결제 금액 = 조회 시점 가격 × 수량. 결제 시점 금액과 달라질 수 있다.", example = "24000")
    private BigDecimal estimatedAmount;

    @Schema(description = "선택 가능한 픽업 날짜. 지난 날짜를 제외하고 오름차순으로 내려준다.")
    private List<LocalDate> pickupDates;

    @Schema(description = "현재 선택된 픽업 날짜. 선택 전이면 null이며, 이 상태로 주문하면 OR005가 난다.", example = "2026-08-01")
    private LocalDate selectedPickupDate;

    @Schema(description = "만료 시각", example = "2026-07-28T14:15:00")
    private LocalDateTime expiresAt;

    @Schema(description = "만료까지 남은 초. 화면 타이머용.", example = "540")
    private Integer remainingSeconds;

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class DropInfo{
        @Schema(description = "드롭 ID", example = "7")
        private Long dropId;

        @Schema(description = "상품명", example = "말차 크루아상")
        private String dropName;

        @Schema(description = "단가", example = "12000")
        private BigDecimal price;

        @Schema(description = "상품 이미지 URL", example = "https://cdn.openbake.com/drops/7.jpg")
        private String imageUrl;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class SellerInfo{
        @Schema(description = "판매자 ID", example = "3")
        private Long sellerId;

        @Schema(description = "베이커리 상호명. 판매자를 찾지 못하면 null.", example = "오픈베이크 연남")
        private String sellerName;
    }
}
