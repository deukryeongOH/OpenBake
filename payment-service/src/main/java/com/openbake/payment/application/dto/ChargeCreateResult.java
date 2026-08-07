package com.openbake.payment.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ChargeCreateResult(
        Long chargeRequestId,
        String pgOrderId,
        BigDecimal amount,
        String orderName,
        LocalDateTime expiresAt
) {}
