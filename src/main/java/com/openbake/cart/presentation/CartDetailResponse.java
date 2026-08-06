package com.openbake.cart.presentation;

import com.openbake.cart.application.CartDetailResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CartDetailResponse(
        @Schema(description = "장바구니 ID", example = "31")
        Long cartId,

        @Schema(description = "담긴 드롭 정보. 조회 시점의 드롭 최신값이다(스냅샷 아님).")
        DropInfo drop,

        @Schema(description = "판매자 정보")
        SellerInfo seller,

        @Schema(description = "수량", example = "2")
        Integer quantity,

        @Schema(description = "예상 결제 금액 = 조회 시점 가격 × 수량. 결제 시점 금액과 달라질 수 있다.", example = "24000")
        BigDecimal estimatedAmount,

        @Schema(description = "선택 가능한 픽업 날짜. 지난 날짜를 제외하고 오름차순으로 내려준다.")
        List<LocalDate> pickupDates,

        @Schema(description = "현재 선택된 픽업 날짜. 선택 전이면 null이며, 이 상태로 주문하면 OR005가 난다.", example = "2026-08-01")
        LocalDate selectedPickupDate,

        @Schema(description = "만료 시각", example = "2026-07-28T14:15:00")
        LocalDateTime expiresAt,

        @Schema(description = "만료까지 남은 초. 화면 타이머용.", example = "540")
        Integer remainingSeconds
) {

    public static CartDetailResponse from(CartDetailResult result) {
        return new CartDetailResponse(
                result.cartId(),
                DropInfo.from(result.drop()),
                SellerInfo.from(result.seller()),
                result.quantity(),
                result.estimatedAmount(),
                result.pickupDates(),
                result.selectedPickupDate(),
                result.expiresAt(),
                result.remainingSeconds()
        );
    }

    public record DropInfo(
            @Schema(description = "드롭 ID", example = "7")
            Long dropId,

            @Schema(description = "상품명", example = "말차 크루아상")
            String dropName,

            @Schema(description = "단가", example = "12000")
            BigDecimal price,

            @Schema(description = "상품 이미지 URL", example = "https://cdn.openbake.com/drops/7.jpg")
            String imageUrl
    ) {
        public static DropInfo from(CartDetailResult.DropInfo drop) {
            return new DropInfo(drop.dropId(), drop.dropName(), drop.price(), drop.imageUrl());
        }
    }

    public record SellerInfo(
            @Schema(description = "판매자 ID", example = "3")
            Long sellerId,

            @Schema(description = "베이커리 상호명. 판매자를 찾지 못하면 null.", example = "오픈베이크 연남")
            String sellerName
    ) {
        public static SellerInfo from(CartDetailResult.SellerInfo seller) {
            return new SellerInfo(seller.sellerId(), seller.sellerName());
        }
    }
}