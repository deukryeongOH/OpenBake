package com.openbake.order.application.port.dto;

import java.time.LocalDate;
import java.util.Set;

/**
 * 드롭 주문의 스냅샷 소스.
 *
 * productId 가 들어 있는 이유는 두 가지다 — 정산이 productId 를 받고,
 * 드롭 재고도 결국 product_inventories 를 쓴다. Drop 이 product_id 를 갖고 있고
 * 어댑터가 이미 Product 를 조회하므로 추가 쿼리 없이 채울 수 있다.
 */
public record DropInfo(
        Long dropId,
        Long productId,
        Long sellerId,
        String name,
        int price,
        String imageUrl,
        Set<LocalDate> pickUpAvailableDates
) {
}
