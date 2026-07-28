package com.openbake.payment.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ChargeCreateResponse(
        @Schema(description = "충전 요청 ID", example = "1")
        Long chargeRequestId,
        @Schema(description = "PG에 보낼 주문번호 (UUID)", example = "a3f1c2e8-9b4d-4c1a-8e5f-2d7b1c3a4e6f")
        String pgOrderId,
        @Schema(description = "충전 금액", example = "50000")
        BigDecimal amount,
        @Schema(description = "결제창에 표시될 주문명", example = "예치금 50,000원 충전")
        String orderName,
        @Schema(description = "충전 요청 만료 시각 (요청 + 30분)", example = "2026-07-17T14:28:40")
        LocalDateTime expiresAt
) {
}
