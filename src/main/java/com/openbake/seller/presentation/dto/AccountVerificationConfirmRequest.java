package com.openbake.seller.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record AccountVerificationConfirmRequest(
        @Schema(description = "계좌 거래내역에서 확인한 4자리 인증 코드", example = "3821")
        @NotBlank String verificationCode
) {}
