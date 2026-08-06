package com.openbake.seller.presentation.dto;

import com.openbake.seller.application.BusinessVerificationCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record BusinessVerificationRequest(
        @Schema(description = "사업자등록번호", example = "123-45-67890")
        @NotBlank @Pattern(regexp = "\\d{3}-\\d{2}-\\d{5}", message = "사업자등록번호 형식이 올바르지 않습니다.") String businessNumber,
        @Schema(description = "사업장 주소", example = "서울시 ...")
        @NotBlank String businessAddress,
        @Schema(description = "사업자등록증 상 대표자명", example = "이세종")
        @NotBlank String businessRepresentativeName
) {
        public BusinessVerificationCommand toCommand() {
                return new BusinessVerificationCommand(businessNumber, businessAddress, businessRepresentativeName);
        }
}
