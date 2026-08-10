package com.openbake.payment.presentation.dto;

import com.openbake.payment.application.dto.ChargeStatusResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ChargeStatusResponse(
        @Schema(description = "충전 요청 ID", example = "1")
        Long chargeRequestId,
        @Schema(description = "충전 금액", example = "50000")
        BigDecimal amount,
        @Schema(description = "충전 상태 (READY/IN_PROGRESS/DONE/FAILED/EXPIRED)", example = "IN_PROGRESS")
        String status,
        @Schema(description = "결제 수단", example = "CARD")
        String method,
        @Schema(description = "PG 실패 코드", example = "REJECT_CARD_PAYMENT")
        String failureCode,
        @Schema(description = "PG 실패 사유", example = "카드 한도 초과")
        String failureReason,
        @Schema(description = "충전 요청 시각", example = "2026-07-17T13:58:40")
        LocalDateTime requestedAt,
        @Schema(description = "PG 승인 시각", example = "2026-07-17T14:02:31")
        LocalDateTime approvedAt,
        @Schema(description = "충전 요청 만료 시각", example = "2026-07-17T14:28:40")
        LocalDateTime expiresAt
) {
    public static ChargeStatusResponse from(ChargeStatusResult result) {
        return new ChargeStatusResponse(
                result.chargeRequestId(), result.amount(), result.status(),
                result.method(), result.failureCode(), result.failureReason(),
                result.requestedAt(), result.approvedAt(), result.expiresAt()
        );
    }
}
