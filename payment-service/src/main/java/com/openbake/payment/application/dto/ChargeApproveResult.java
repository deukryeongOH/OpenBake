package com.openbake.payment.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ChargeApproveResult(
        Long chargeRequestId,
        String status,
        BigDecimal chargedAmount,
        BigDecimal balanceAfter,
        String method,
        LocalDateTime approvedAt
) {}
