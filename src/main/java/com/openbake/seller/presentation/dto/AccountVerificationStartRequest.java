package com.openbake.seller.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AccountVerificationStartRequest(
        @NotBlank String bankCode,
        @NotBlank @Pattern(regexp = "\\d{10,14}", message = "계좌번호 형식이 올바르지 않습니다.") String accountNumber,
        @NotBlank String accountHolder
) {}
