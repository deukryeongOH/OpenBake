package com.openbake.payment.presentation.internal.dto;

import com.openbake.payment.application.dto.PaymentIdempotentResult;

public record PaymentResultResponse(
        String status,
        String message
) {
    public static PaymentResultResponse from(PaymentIdempotentResult result) {
        return new PaymentResultResponse(result.status(), result.failReason());
    }

    public static PaymentResultResponse success() {
        return new PaymentResultResponse("SUCCESS", null);
    }
}
