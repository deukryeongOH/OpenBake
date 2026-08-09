package com.openbake.seller.presentation.dto;

import com.openbake.seller.application.ApplicationCreateCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record ApplicationCreateRequest(
        @Schema(description = "베이커리 상호명", example = "세종베이커리")
        @NotBlank String bakeryName,
        @Schema(description = "사업자등록번호", example = "123-45-67890")
        @NotBlank String businessNumber,
        @Schema(description = "사업장 주소", example = "서울시 ...")
        @NotBlank String businessAddress,
        @Schema(description = "사업자등록증 상 대표자명", example = "이세종")
        @NotBlank String businessRepresentativeName
) {
        public ApplicationCreateCommand toCommand() {
                return new ApplicationCreateCommand(bakeryName, businessNumber, businessAddress, businessRepresentativeName);
        }
}
