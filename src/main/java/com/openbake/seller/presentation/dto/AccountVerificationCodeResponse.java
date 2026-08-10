package com.openbake.seller.presentation.dto;

import com.openbake.seller.application.AccountVerificationCodeResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record AccountVerificationCodeResponse(
        @Schema(description = "계좌 인증 요청 ID", example = "vr_20260723_001")
        String verificationRequestId,
        @Schema(description = "[DEV 전용] 목업 인증 코드", example = "3821")
        String code,
        @Schema(description = "인증 코드 만료 시각")
        LocalDateTime expiresAt
) {
        public static AccountVerificationCodeResponse from(AccountVerificationCodeResult result) {
                return new AccountVerificationCodeResponse(result.verificationRequestId(), result.code(), result.expiresAt());
        }
}
