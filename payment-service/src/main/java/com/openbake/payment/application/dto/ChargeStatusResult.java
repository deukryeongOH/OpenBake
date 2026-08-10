package com.openbake.payment.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ChargeStatusResult(
        Long chargeRequestId,
        BigDecimal amount,
        String status,
        String method,
        String failureCode,
        String failureReason,
        LocalDateTime requestedAt,
        LocalDateTime approvedAt,
        LocalDateTime expiresAt
) {}
