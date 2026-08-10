package com.openbake.payment.application.dto;

import java.math.BigDecimal;

public record DevChargeCommand(
        Long memberId,
        BigDecimal amount
) {}
