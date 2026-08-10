package com.openbake.payment.application.dto;

import com.openbake.payment.domain.PaymentRecord;

public record PaymentIdempotentResult(
        String status,
        String failReason
) {
    public static PaymentIdempotentResult from(PaymentRecord record) {
        return new PaymentIdempotentResult(
                record.getStatus().name(),
                record.getFailReason()
        );
    }

    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }
}
