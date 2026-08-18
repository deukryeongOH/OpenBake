package com.openbake.cart.application;

import com.openbake.cart.domain.CartItem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 가격과 재고는 조회 시점 최신값이며 스냅샷이 아니다.
 */
public record CartDetailResult(
        Long cartId,
        List<Item> items,
        BigDecimal totalAmount
) {

    //장바구니를 아직 만든 적이 없는 회원. 페이지는 열려야 하므로 빈 목록으로 응답한다.
    public static CartDetailResult empty() {
        return new CartDetailResult(null, List.of(), BigDecimal.ZERO);
    }

    public record Item(
            Long cartItemId,
            Long productId,
            //판매자 식별자. 프론트가 판매자별로 항목을 묶을 때 쓴다.
            //상호명으로 묶으면 판매자가 상호를 바꿨을 때 한 판매자가 둘로 쪼개지고,
            //상호명이 같은 다른 판매자가 하나로 합쳐진다.
            Long sellerId,
            String productName,
            String bakeryName,
            String imageUrl,
            BigDecimal price,
            //담을 때의 단가. 비교용 기준값이며 금액 계산에는 쓰지 않는다.
            BigDecimal addedPrice,
            boolean priceChanged,
            int quantity,
            BigDecimal estimatedAmount,
            LocalDate pickUpDate,
            List<LocalDate> pickUpAvailableDates,
            int remainQuantity,
            boolean orderable,
            CartItemStatus status
    ) {

        /**
         * 상품이 삭제된 항목. product 를 읽을 수 없으므로 담을 때 저장해 둔 값만 채운다.
         */
        public static Item unavailable(CartItem item, CartItemStatus status) {
            return new Item(
                    item.getCartItemId(),
                    item.getProductId(),
                    //상품이 사라지면 판매자를 알 수 없다. 남는 단서는 담을 때 저장해 둔 상호명뿐이다.
                    null,
                    null,
                    item.getBakeryName(),
                    null,
                    null,
                    //현재 가격을 읽을 수 없으니 비교 자체가 불가능하다. 기준값만 그대로 내려준다.
                    item.getAddedPrice() == null ? null : BigDecimal.valueOf(item.getAddedPrice()),
                    false,
                    item.getQuantity(),
                    BigDecimal.ZERO,
                    item.getPickUpDate(),
                    List.of(),
                    0,
                    false,
                    status
            );
        }
    }
}
