package com.openbake.settlement.presentation;

import com.openbake.settlement.application.ReceivePurchaseConfirmedCommand;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 주문 도메인이 정산 도메인으로 전달하는 구매확정 이벤트 요청입니다.
 * HTTP 요청으로 받은 데이터를
 * 애플리케이션 계층의 Command로 변환합니다.
 */
public record PurchaseConfirmedRequest(
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
    /**
     * HTTP 요청 DTO를 애플리케이션 Command로 변환합니다.
     */
    public ReceivePurchaseConfirmedCommand toCommand() {
        return new ReceivePurchaseConfirmedCommand(
                eventId,
                orderId,
                orderItemId,
                sellerId,
                productId,
                productNameSnapshot,
                quantity,
                grossAmount,
                purchaseConfirmedAt
        );
    }
}