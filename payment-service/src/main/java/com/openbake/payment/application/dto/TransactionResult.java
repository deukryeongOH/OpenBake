package com.openbake.payment.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResult(
        Long id,
        String transactionType,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String description,
        String referenceType,
        Long referenceId,
        LocalDateTime createdAt
) {}
