package com.openbake.cart.application.port.dto;

/**
 * 재고 선점 상태.
 *
 * drop 의 EntryStatus 를 그대로 넘기지 않고 "선점됐는가"라는 판정 결과만 담는다.
 * cart 에 필요한 건 상태값 자체가 아니라 담기를 허용할지 여부다.
 */
public record ReservationInfo(
        boolean reserved,
        int selectQuantity
) {
}
