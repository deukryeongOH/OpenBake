package com.openbake.seller.presentation;

import com.openbake.common.response.ApiResponse;
import com.openbake.seller.application.SellerService;
import com.openbake.seller.presentation.dto.AccountVerificationCodeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// [DEV 전용] 목업 인증 코드 조회. 운영(prod) 프로파일에서는 빈 자체가 등록되지 않아 API가 존재하지 않음.
@Profile({"local", "dev"})
@RestController
@RequestMapping("/api/v1/sellers")
@RequiredArgsConstructor
public class SellerDevController {

    private final SellerService sellerService;

    @GetMapping("/settlement-account/verification-requests/{verificationRequestId}/mock-code")
    public ApiResponse<AccountVerificationCodeResponse> getMockVerificationCode(
            @PathVariable String verificationRequestId) {
        return ApiResponse.ok(sellerService.getMockVerificationCode(verificationRequestId));
    }
}
