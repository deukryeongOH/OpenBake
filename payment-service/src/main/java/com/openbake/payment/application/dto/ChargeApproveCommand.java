package com.openbake.payment.application.dto;

import java.math.BigDecimal;

public record ChargeApproveCommand(
        Long memberId,
        String paymentKey,
        String orderId,
        BigDecimal amount
) {}
