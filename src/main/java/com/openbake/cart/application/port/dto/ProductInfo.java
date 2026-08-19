package com.openbake.cart.application.port.dto;

import java.time.LocalDate;
import java.util.Set;

/**
 * cart 가 일반 상품에서 필요로 하는 값만 담은 DTO.
 * 가격은 product 가 int 로 관리하므로 그대로 받고, 변환은 cart 가 한다.
 *
 * product 의 타입(Type, ProductStatus)은 이 DTO 로 넘어오지 않는다.
 * cart 가 필요한 건 '일반 상품인가', '품절인가' 두 판정뿐이라 어댑터에서 boolean 으로 바꿔 담는다.
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
        //일반 상품인가. 드롭 상품은 장바구니를 거치지 않는다.
        boolean generalType,
        //품절 판정의 유일한 기준. 재고가 0이 되면 product 가 상태를 바꾸므로
        //cart 는 재고로 품절을 따로 추론하지 않고 이 값만 본다(담기 차단/조회 표시 모두).
        boolean soldOut,
        Set<LocalDate> pickUpAvailableDates,
        int remainQuantity
) {
}
