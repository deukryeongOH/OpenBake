package com.openbake.payment.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        @Schema(description = "거래 내역 ID", example = "1")
        Long id,
        @Schema(description = "거래 유형 (CHARGE/PAYMENT/REFUND)", example = "CHARGE")
        String transactionType,
        @Schema(description = "거래 금액 (부호 있음: + 증가, - 감소)", example = "50000")
        BigDecimal amount,
        @Schema(description = "거래 후 잔액", example = "50000")
        BigDecimal balanceAfter,
        @Schema(description = "거래 설명", example = "예치금 충전")
        String description,
        @Schema(description = "원인 유형 (CHARGE_REQUEST/ORDER_PAYMENT)", example = "CHARGE_REQUEST")
        String referenceType,
        @Schema(description = "원인 ID", example = "1")
        Long referenceId,
        @Schema(description = "거래 일시", example = "2026-07-17T13:58:40")
        LocalDateTime createdAt
) {
}
