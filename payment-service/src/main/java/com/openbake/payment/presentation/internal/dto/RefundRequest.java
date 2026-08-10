package com.openbake.payment.presentation.internal.dto;

public record RefundRequest(
        String idempotencyKey,
        Long orderId
) {}
