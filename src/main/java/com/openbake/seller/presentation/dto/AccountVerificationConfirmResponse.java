package com.openbake.seller.presentation.dto;

import com.openbake.seller.application.AccountVerificationConfirmResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record AccountVerificationConfirmResponse(
        @Schema(description = "인증 성공 여부", example = "true")
        boolean verified,
        @Schema(description = "계좌 인증 완료 시각")
        LocalDateTime accountVerifiedAt
) {
        public static AccountVerificationConfirmResponse from(AccountVerificationConfirmResult result) {
                return new AccountVerificationConfirmResponse(result.verified(), result.accountVerifiedAt());
        }
}
