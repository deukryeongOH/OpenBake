package com.openbake.order.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 정산으로 전송할 구매확정 이벤트 본문.
 * 정산 실제 수신 DTO(settlement.presentation.PurchaseConfirmedRequest)와 필드가 1:1로 맞아야 한다.
 * (명세서의 eventVersion/occurredAt/orderLineId/commissionRateSnapshot 는 수신 코드에 없어 넣지 않는다.
 *  그 값들은 order_outbox_events 테이블 컬럼으로만 존재한다.)
 * 필드명이 곧 JSON 키가 되므로 이름을 바꾸면 정산이 못 받는다.
 */
public record PurchaseConfirmedPayload(
        String eventId,
        Long orderId,
        Long orderItemId,
        Long sellerId,
        Long dropId,
        String productNameSnapshot,
        Integer quantity,
        BigDecimal grossAmount,
        OffsetDateTime purchaseConfirmedAt
) {
}
