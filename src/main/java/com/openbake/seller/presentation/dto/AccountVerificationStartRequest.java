package com.openbake.seller.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AccountVerificationStartRequest(
        @Schema(description = "은행 코드", example = "088")
        @NotBlank String bankCode,
        @Schema(description = "계좌번호 (하이픈 없이)", example = "110123456789")
        @NotBlank @Pattern(regexp = "\\d{10,14}", message = "계좌번호 형식이 올바르지 않습니다.") String accountNumber,
        @Schema(description = "예금주명", example = "이세종")
        @NotBlank String accountHolder
) {}
