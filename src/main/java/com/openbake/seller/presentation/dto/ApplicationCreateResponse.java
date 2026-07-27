package com.openbake.seller.presentation.dto;

import com.openbake.seller.domain.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record ApplicationCreateResponse(
        @Schema(description = "생성된 판매자 ID", example = "1")
        Long sellerId,
        @Schema(description = "신청 회원 ID", example = "1")
        Long memberId,
        @Schema(description = "베이커리 상호명", example = "세종베이커리")
        String bakeryName,
        @Schema(description = "입점 신청 상태", example = "PENDING")
        ApplicationStatus applicationStatus
) {}
