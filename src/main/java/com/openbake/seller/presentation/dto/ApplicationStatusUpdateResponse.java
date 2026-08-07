package com.openbake.seller.presentation.dto;

import com.openbake.seller.application.ApplicationStatusUpdateResult;
import com.openbake.seller.domain.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record ApplicationStatusUpdateResponse(
        @Schema(description = "판매자 ID", example = "1")
        Long sellerId,
        @Schema(description = "변경된 입점 신청 상태", example = "REJECTED")
        ApplicationStatus applicationStatus,
        @Schema(description = "반려 사유 (반려 시)", example = "제출한 사업장 주소가 실제 등록 주소와 일치하지 않습니다.")
        String rejectReason,
        @Schema(description = "처리 시각")
        LocalDateTime updatedAt
) {
        public static ApplicationStatusUpdateResponse from(ApplicationStatusUpdateResult result) {
                return new ApplicationStatusUpdateResponse(result.sellerId(), result.applicationStatus(), result.rejectReason(), result.updatedAt());
        }
}
