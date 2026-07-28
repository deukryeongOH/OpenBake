package com.openbake.payment.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ChargeApproveResponse(
        @Schema(description = "충전 요청 ID", example = "1")
        Long chargeRequestId,
        @Schema(description = "충전 상태 (DONE 고정)", example = "DONE")
        String status,
        @Schema(description = "충전된 금액", example = "50000")
        BigDecimal chargedAmount,
        @Schema(description = "충전 후 예치금 잔액", example = "50000")
        BigDecimal balanceAfter,
        @Schema(description = "결제 수단", example = "CARD")
        String method,
        @Schema(description = "PG 승인 시각", example = "2026-07-17T14:02:31")
        LocalDateTime approvedAt
) {
}
