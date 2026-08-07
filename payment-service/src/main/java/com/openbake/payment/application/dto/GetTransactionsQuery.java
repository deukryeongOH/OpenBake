package com.openbake.payment.application.dto;

import com.openbake.payment.domain.TransactionType;

public record GetTransactionsQuery(
        Long memberId,
        TransactionType transactionType,
        int page,
        int size
) {}
