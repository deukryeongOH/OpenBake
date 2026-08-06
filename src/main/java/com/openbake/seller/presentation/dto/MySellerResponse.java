package com.openbake.seller.presentation.dto;

import com.openbake.seller.application.MySellerResult;
import com.openbake.seller.domain.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record MySellerResponse(
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
        @Schema(description = "반려 사유 (반려 상태가 아니면 null)", example = "제출한 사업장 주소가 실제 등록 주소와 일치하지 않습니다.")
        String rejectReason,
        @Schema(description = "정산 은행 코드", example = "088")
        String settlementBankCode,
        @Schema(description = "마스킹된 정산 계좌번호 (원본은 어떤 조회 API에서도 반환하지 않음)", example = "110-****-5678")
        String settlementAccountNumberMasked,
        @Schema(description = "계좌 인증 완료 여부", example = "true")
        boolean accountVerified,
        @Schema(description = "계좌 인증 완료 시각")
        LocalDateTime accountVerifiedAt
) {
        public static MySellerResponse from(MySellerResult result) {
                return new MySellerResponse(
                        result.sellerId(),
                        result.memberId(),
                        result.bakeryName(),
                        result.businessNumber(),
                        result.applicationStatus(),
                        result.rejectReason(),
                        result.settlementBankCode(),
                        result.settlementAccountNumberMasked(),
                        result.accountVerified(),
                        result.accountVerifiedAt()
                );
        }
}
