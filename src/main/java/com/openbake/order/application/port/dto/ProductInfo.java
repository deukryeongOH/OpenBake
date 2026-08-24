package com.openbake.order.application.port.dto;

import java.time.LocalDate;
import java.util.Set;

/**
 * order 가 일반 상품에서 필요로 하는 값만 담은 DTO.
 *
 * product 의 타입(Type, ProductStatus)은 여기로 넘어오지 않는다. order 가 필요한 건
 * '일반 상품인가', '품절인가' 두 판정뿐이라 어댑터에서 boolean 으로 바꿔 담는다.
 *
 * price 는 product 가 int 로 관리하므로 그대로 받고, BigDecimal 변환은 order 가 한다.
 * remainQuantity 는 조회 시점 값이라 <b>재고 보장의 근거가 아니다</b> —
 * 주문 생성 시 안내용 확인에만 쓰고, 실제 방어는 결제 후 조건부 차감이 한다.
 */
public record ProductInfo(
        Long productId,
        Long sellerId,
        String name,
        int price,
        String imageUrl,
        //일반 상품인가. 바로 주문 경로에서 드롭 상품을 끼워 넣어 선점을 우회하는 것을 막는다.
        boolean generalType,
        //품절 판정의 유일한 기준. 재고가 0이 되면 product 가 상태를 바꾼다.
        boolean soldOut,
        Set<LocalDate> pickUpAvailableDates,
        int remainQuantity
) {
}
