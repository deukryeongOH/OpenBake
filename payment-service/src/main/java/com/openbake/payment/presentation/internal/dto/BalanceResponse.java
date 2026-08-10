package com.openbake.payment.presentation.internal.dto;

import com.openbake.payment.application.dto.DepositResult;

import java.math.BigDecimal;

public record BalanceResponse(
        Long memberId,
        BigDecimal balance
) {
    public static BalanceResponse from(DepositResult result) {
        return new BalanceResponse(result.memberId(), result.balance());
    }
}
