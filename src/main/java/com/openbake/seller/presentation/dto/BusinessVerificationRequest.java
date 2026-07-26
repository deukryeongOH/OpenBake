package com.openbake.seller.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record BusinessVerificationRequest(
        @NotBlank @Pattern(regexp = "\\d{3}-\\d{2}-\\d{5}", message = "사업자등록번호 형식이 올바르지 않습니다.") String businessNumber,
        @NotBlank String businessAddress,
        @NotBlank String businessRepresentativeName
) {}
