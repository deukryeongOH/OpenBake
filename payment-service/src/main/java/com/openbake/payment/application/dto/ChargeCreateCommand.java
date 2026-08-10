package com.openbake.payment.application.dto;

import java.math.BigDecimal;

public record ChargeCreateCommand(
        Long memberId,
        BigDecimal amount
) {}
