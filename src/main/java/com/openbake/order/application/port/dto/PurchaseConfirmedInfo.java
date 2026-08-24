package com.openbake.order.application.port.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 구매확정 사실. 정산이 정산 대상을 만드는 근거다.
 *
 * <b>항목 단위로 발행한다.</b> 정산은 이미 UNIQUE(order_item_id) 로 항목 단위를 전제하고 있다.
 *
 * dropId 가 아니라 productId 를 보낸다 — 일반 상품에는 dropId 가 없고, 정산은 드롭 회차를
 * 로직으로 구분하지 않아 dropId 를 남겨도 읽는 코드가 없다.
 * grossAmount 는 <b>주문 전체 합계가 아니라 항목 소계</b>다. 전체를 보내면 한 주문에
 * 판매자가 둘일 때 판매자마다 전체 금액이 정산돼 받은 돈보다 많이 지급된다.
 */
public record PurchaseConfirmedInfo(
        String eventId,
        Long orderId,
        Long orderItemId,
        Long sellerId,
        Long productId,
        String productNameSnapshot,
        Integer quantity,
        BigDecimal grossAmount,
        OffsetDateTime purchaseConfirmedAt
) {
}
