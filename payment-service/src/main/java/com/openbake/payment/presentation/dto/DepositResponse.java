package com.openbake.payment.presentation.dto;

import com.openbake.payment.application.dto.DepositResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

public record DepositResponse(
        @Schema(description = "회원 ID", example = "1")
        Long memberId,
        @Schema(description = "현재 예치금 잔액", example = "45000")
        BigDecimal balance,
        @Schema(description = "진행 중인 충전 존재 여부 (READY/IN_PROGRESS)", example = "false")
        boolean hasChargeInProgress
) {
    public static DepositResponse from(DepositResult result) {
        return new DepositResponse(
                result.memberId(), result.balance(), result.hasChargeInProgress()
        );
    }
}
