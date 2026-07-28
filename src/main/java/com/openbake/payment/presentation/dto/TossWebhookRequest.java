package com.openbake.payment.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TossWebhookRequest(
        @Schema(description = "이벤트 유형", example = "PAYMENT_STATUS_CHANGED")
        String eventType,
        @Schema(description = "웹훅 데이터")
        TossWebhookData data
) {
    public record TossWebhookData(
            @Schema(description = "PG 결제 키", example = "tviva20260717140212ABCD1")
            String paymentKey,
            @Schema(description = "주문번호", example = "a3f1c2e8-9b4d-4c1a-8e5f-2d7b1c3a4e6f")
            String orderId,
            @Schema(description = "결제 상태", example = "DONE")
            String status,
            @Schema(description = "결제 금액", example = "50000")
            Integer totalAmount
    ) {}
}
