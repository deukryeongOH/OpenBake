package com.openbake.payment.presentation.internal.dto;

import java.math.BigDecimal;

public record RefundRequest(
        String idempotencyKey,
        Long orderId,
        Long memberId,
        BigDecimal amount
) {}
