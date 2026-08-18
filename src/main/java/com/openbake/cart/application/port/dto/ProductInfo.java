package com.openbake.cart.application.port.dto;

import java.time.LocalDate;
import java.util.Set;

/**
 * cart 가 일반 상품에서 필요로 하는 값만 담은 DTO.
 * 가격은 product 가 int 로 관리하므로 그대로 받고, 변환은 cart 가 한다.
 *
 * remainQuantity 는 조회 시점 값이다. 장바구니는 재고를 선점하지 않으므로
 * 이 값은 담기 검증과 화면 표시에만 쓰고 재고 보장 근거로 삼지 않는다.
 */
public record ProductInfo(
        Long productId,
        Long sellerId,
        String name,
        int price,
        String imageUrl,
        Set<LocalDate> pickUpAvailableDates,
        int remainQuantity
) {

    public boolean isSoldOut() {
        return remainQuantity <= 0;
    }
}
