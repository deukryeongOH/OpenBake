package com.openbake.payment.application.dto;

import java.math.BigDecimal;

public record DepositResult(
        Long memberId,
        BigDecimal balance,
        boolean hasChargeInProgress
) {}
