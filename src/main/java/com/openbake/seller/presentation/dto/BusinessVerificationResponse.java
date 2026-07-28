package com.openbake.seller.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record BusinessVerificationResponse(
        @Schema(description = "인증 성공 여부", example = "true")
        boolean verified,
        @Schema(description = "사업자등록번호", example = "123-45-67890")
        String businessNumber,
        @Schema(description = "인증 처리 시각")
        LocalDateTime verifiedAt
) {}
