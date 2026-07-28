package com.openbake.seller.presentation;

import com.openbake.common.response.ApiResponse;
import com.openbake.seller.application.SellerService;
import com.openbake.seller.presentation.dto.AccountVerificationCodeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// [DEV 전용] 목업 인증 코드 조회. 운영(prod) 프로파일에서는 빈 자체가 등록되지 않아 API가 존재하지 않음.
@Tag(name = "Seller", description = "사업자/계좌 인증, 판매자 입점 신청·승인, 판매자 조회")
@Profile({"local", "dev"})
@RestController
@RequestMapping("/api/v1/sellers")
@RequiredArgsConstructor
public class SellerDevController {

    private final SellerService sellerService;

    @Operation(
            summary = "[DEV 전용] 목업 인증 코드 조회",
            description = "계좌 인증 요청으로 발급된 인증 코드를 개발/데모용으로 조회합니다. local/dev 프로파일에서만 노출됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "C003 대상을 찾을 수 없습니다. (요청이 존재하지 않거나 만료됨)")
    })
    @GetMapping("/settlement-account/verification-requests/{verificationRequestId}/mock-code")
    public ApiResponse<AccountVerificationCodeResponse> getMockVerificationCode(
            @Parameter(description = "계좌 인증 요청 ID", example = "vr_20260723_001") @PathVariable String verificationRequestId) {
        return ApiResponse.ok(sellerService.getMockVerificationCode(verificationRequestId));
    }
}
