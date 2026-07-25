package com.openbake.seller.presentation;

import com.openbake.common.response.ApiResponse;
import com.openbake.seller.application.SellerService;
import com.openbake.seller.presentation.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sellers")
@RequiredArgsConstructor
public class SellerController {

    private final SellerService sellerService;

    @PostMapping("/business-verifications")
    public ApiResponse<BusinessVerificationResponse> verifyBusiness(@Valid @RequestBody BusinessVerificationRequest request) {
        return ApiResponse.ok(sellerService.verifyBusiness(request));
    }

    @PostMapping("/settlement-account/verification-requests")
    public ApiResponse<AccountVerificationStartResponse> requestAccountVerification(
            @Valid @RequestBody AccountVerificationStartRequest request) {
        return ApiResponse.ok(sellerService.requestAccountVerification(request));
    }

    @PostMapping("/settlement-account/verification-requests/{verificationRequestId}/verify")
    public ApiResponse<AccountVerificationConfirmResponse> verifyAccount(
            @PathVariable String verificationRequestId,
            @Valid @RequestBody AccountVerificationConfirmRequest request) {
        return ApiResponse.ok(sellerService.verifyAccount(verificationRequestId, request));
    }

    @PostMapping("/apply")
    public ApiResponse<ApplicationCreateResponse> applySeller(@Valid @RequestBody ApplicationCreateRequest request) {
        return ApiResponse.ok(sellerService.applySeller(request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<ApplicationStatusUpdateResponse> updateApplicationStatus(
            @PathVariable Long id,
            @Valid @RequestBody ApplicationStatusUpdateRequest request) {
        return ApiResponse.ok(sellerService.updateApplicationStatus(id, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<SellerResponse> getSeller(@PathVariable Long id) {
        return ApiResponse.ok(sellerService.getSeller(id));
    }

}
