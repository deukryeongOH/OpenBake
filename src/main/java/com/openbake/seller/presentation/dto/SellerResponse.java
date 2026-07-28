package com.openbake.seller.presentation.dto;

import com.openbake.seller.domain.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record SellerResponse(
        @Schema(description = "판매자 ID", example = "1")
        Long sellerId,
        @Schema(description = "회원 ID", example = "1")
        Long memberId,
        @Schema(description = "베이커리 상호명", example = "세종베이커리")
        String bakeryName,
        @Schema(description = "사업자등록번호", example = "123-45-67890")
        String businessNumber,
        @Schema(description = "입점 신청 상태", example = "APPROVED")
        ApplicationStatus applicationStatus,
        @Schema(description = "정산 은행 코드", example = "088")
        String settlementBankCode,
        @Schema(description = "마스킹된 정산 계좌번호 (원본은 어떤 조회 API에서도 반환하지 않음)", example = "110-****-5678")
        String settlementAccountNumberMasked,
        @Schema(description = "계좌 인증 완료 여부", example = "true")
        boolean accountVerified,
        @Schema(description = "계좌 인증 완료 시각")
        LocalDateTime accountVerifiedAt
) {}
