package com.openbake.cart.presentation;

import com.openbake.cart.application.CartDetailResult;
import com.openbake.cart.application.CartItemStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CartDetailResponse(
        @Schema(description = "장바구니 ID. 장바구니 만든 적이 없으면 null.", example = "31")
        Long cartId,

        @Schema(description = "담긴 상품들")
        List<Item> items,

        @Schema(description = "주문 가능한 항목만 합산한 예상 결제 금액", example = "36000")
        BigDecimal totalAmount
) {

    public static CartDetailResponse from(CartDetailResult result) {
        return new CartDetailResponse(
                result.cartId(),
                result.items().stream().map(Item::from).toList(),
                result.totalAmount()
        );
    }

    public record Item(
            @Schema(description = "장바구니 항목 ID", example = "104")
            Long cartItemId,

            @Schema(description = "상품 ID", example = "7")
            Long productId,

            @Schema(description = "판매자 ID. 상품들을 판매자별로 묶어 보여줄때 쓰인다.", example = "3")
            Long sellerId,

            @Schema(description = "상품명. 상품이 삭제됐으면 null.", example = "말차 크루아상")
            String productName,

            @Schema(description = "판매자 상호명. 조회 시점 최신값이라 판매자가 상호를 바꾸면 그대로 바뀐다(별도 안내 없음). 상품이 삭제된 항목은 담을 때 저장해 둔 값이다.", example = "오픈베이크 베이커리")
            String bakeryName,

            @Schema(description = "상품 이미지 URL", example = "https://cdn.openbake.com/products/7.jpg")
            String imageUrl,

            @Schema(description = "단가. 조회 시점 최신값이며 스냅샷이 아니다. 결제 금액은 항상 이 값을 기준으로 한다.", example = "12000")
            BigDecimal price,

            @Schema(description = "담을 때의 단가. priceChanged가 true면 'addedPrice → price'로 변동 폭을 안내할 수 있다. 이 컬럼이 생기기 전에 담긴 항목은 null.", example = "11000")
            BigDecimal addedPrice,

            @Schema(description = "담을 때보다 가격이 달라졌는지, 오른 경우와 내린 경우 모두 true", example = "true")
            boolean priceChanged,

            @Schema(description = "담긴 수량", example = "3")
            int quantity,

            @Schema(description = "단가*수량", example = "36000")
            BigDecimal estimatedAmount,

            @Schema(description = "선택된 픽업 날짜. 고르지 않았으면 null이고, 이 상태로는 주문할 수 없다.", example = "2026-08-20")
            LocalDate pickUpDate,

            @Schema(description = "선택 가능한 픽업 날짜. 지난 날짜를 제외하고 오름차순으로 내려준다.")
            List<LocalDate> pickUpAvailableDates,

            @Schema(description = "현재 남은 재고", example = "12")
            int remainQuantity,

            @Schema(description = "지금 주문할 수 있는지. false면 프론트가 항목과 주문 버튼을 비활성 처리.", example = "true")
            boolean orderable,

            @Schema(description = "주문 불가 사유. ORDERABLE / PRODUCT_DELETED / SOLD_OUT / INSUFFICIENT_STOCK / PICKUP_DATE_UNAVAILABLE", example = "ORDERABLE")
            CartItemStatus status
    ) {

        public static Item from(CartDetailResult.Item item) {
            return new Item(
                    item.cartItemId(),
                    item.productId(),
                    item.sellerId(),
                    item.productName(),
                    item.bakeryName(),
                    item.imageUrl(),
                    item.price(),
                    item.addedPrice(),
                    item.priceChanged(),
                    item.quantity(),
                    item.estimatedAmount(),
                    item.pickUpDate(),
                    item.pickUpAvailableDates(),
                    item.remainQuantity(),
                    item.orderable(),
                    item.status()
            );
        }
    }
}
