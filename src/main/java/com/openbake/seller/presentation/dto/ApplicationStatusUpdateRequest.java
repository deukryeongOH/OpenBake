package com.openbake.seller.presentation.dto;

import com.openbake.seller.application.ApplicationStatusUpdateCommand;
import com.openbake.seller.domain.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record ApplicationStatusUpdateRequest(
        @Schema(description = "변경할 입점 신청 상태", example = "REJECTED")
        @NotNull ApplicationStatus applicationStatus,
        @Schema(description = "반려 사유 (반려 시)", example = "제출한 사업장 주소가 실제 등록 주소와 일치하지 않습니다.")
        String rejectReason
) {
        public ApplicationStatusUpdateCommand toCommand() {
                return new ApplicationStatusUpdateCommand(applicationStatus, rejectReason);
        }
}
