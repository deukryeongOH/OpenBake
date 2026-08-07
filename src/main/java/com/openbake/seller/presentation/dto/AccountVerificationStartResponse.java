package com.openbake.seller.presentation.dto;

import com.openbake.seller.application.AccountVerificationStartResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record AccountVerificationStartResponse(
        @Schema(description = "계좌 인증 요청 ID", example = "vr_20260723_001")
        String verificationRequestId,
        @Schema(description = "인증 코드 만료 시각")
        LocalDateTime expiresAt
) {
        public static AccountVerificationStartResponse from(AccountVerificationStartResult result) {
                return new AccountVerificationStartResponse(result.verificationRequestId(), result.expiresAt());
        }
}
