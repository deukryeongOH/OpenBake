package com.openbake.payment.presentation.internal.dto;

import java.math.BigDecimal;

public record PayRequest(
        String idempotencyKey,
        Long orderId,
        Long memberId,
        BigDecimal amount
) {}
