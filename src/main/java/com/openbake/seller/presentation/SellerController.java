package com.openbake.seller.presentation;

import com.openbake.common.response.ApiResponse;
import com.openbake.seller.application.SellerService;
import com.openbake.seller.domain.ApplicationStatus;
import com.openbake.seller.presentation.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Seller", description = "사업자/계좌 인증, 판매자 입점 신청·승인, 판매자 조회")
@RestController
@RequestMapping("/api/v1/sellers")
@RequiredArgsConstructor
public class SellerController {

    private final SellerService sellerService;

    @Operation(
            summary = "사업자 정보 인증 (mock)",
            description = "사업자등록번호/사업장 주소/대표자명이 등록된 정보와 일치하는지 대조만 하는 mock 검증입니다. 결과를 저장하지 않으며, 입점 신청(apply)에서 동일 정보로 다시 한번 대조합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "C001 잘못된 요청입니다. / SE001 사업자 인증에 실패했습니다.")
    })
    @PostMapping("/business-verifications")
    public ApiResponse<BusinessVerificationResponse> verifyBusiness(@Valid @RequestBody BusinessVerificationRequest request) {
        return ApiResponse.ok(sellerService.verifyBusiness(request));
    }

    @Operation(
            summary = "계좌 인증 요청 (1원 송금 mock)",
            description = "계좌 정보를 제출하면 1원 송금(mock)으로 4자리 인증 코드를 발급합니다. Seller가 없으면 memberId 기준으로 임시 저장, 이미 Seller가 있으면(승인 후 계좌 변경) 즉시 계좌 정보를 갱신하고 accountVerified를 false로 리셋합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "SE002 은행 코드 또는 계좌번호 형식이 올바르지 않습니다.")
    })
    @PostMapping("/settlement-account/verification-requests")
    public ApiResponse<AccountVerificationStartResponse> requestAccountVerification(
            @Valid @RequestBody AccountVerificationStartRequest request) {
        return ApiResponse.ok(sellerService.requestAccountVerification(request));
    }

    @Operation(
            summary = "계좌 인증 확인",
            description = "계좌 인증 코드가 일치하는지 확인합니다. 성공 시 memberId 기준 인증 완료 상태가 되고, 이미 Seller가 있으면 accountVerified/accountVerifiedAt도 즉시 갱신됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "SE003 인증 코드가 일치하지 않습니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "410", description = "SE004 인증 유효 시간이 만료되었습니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "C003 대상을 찾을 수 없습니다.")
    })
    @PostMapping("/settlement-account/verification-requests/{verificationRequestId}/verify")
    public ApiResponse<AccountVerificationConfirmResponse> verifyAccount(
            @Parameter(description = "계좌 인증 요청 ID", example = "vr_20260723_001") @PathVariable String verificationRequestId,
            @Valid @RequestBody AccountVerificationConfirmRequest request) {
        return ApiResponse.ok(sellerService.verifyAccount(verificationRequestId, request));
    }

    @Operation(
            summary = "판매자 입점 신청",
            description = "판매자(베이커리) 입점을 신청합니다. 사업자 정보는 이 요청에서 다시 대조하고, 계좌 정보는 미리 인증 완료되어 있어야 합니다(요청 바디로 받지 않음). 회원당 1건만 신청 가능합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "C001 잘못된 요청입니다. / SE001 사업자 인증에 실패했습니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "SE005 이미 판매자 신청을 완료한 회원입니다. / SE006 계좌 인증이 완료되지 않았습니다.")
    })
    @PostMapping("/apply")
    public ApiResponse<ApplicationCreateResponse> applySeller(@Valid @RequestBody ApplicationCreateRequest request) {
        return ApiResponse.ok(sellerService.applySeller(request));
    }

    @Operation(
            summary = "입점 승인/반려 처리 (admin)",
            description = "관리자가 판매자 입점 신청을 승인/반려합니다. admin 권한이 필요합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "SE007 권한이 없습니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "C003 대상을 찾을 수 없습니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "C002 처리할 수 없는 상태입니다.")
    })
    @PatchMapping("/{id}/status")
    public ApiResponse<ApplicationStatusUpdateResponse> updateApplicationStatus(
            @Parameter(description = "대상 판매자 ID", example = "1") @PathVariable Long id,
            @Valid @RequestBody ApplicationStatusUpdateRequest request) {
        return ApiResponse.ok(sellerService.updateApplicationStatus(id, request));
    }

    @Operation(
            summary = "내 판매자 신청 조회",
            description = "로그인한 회원 본인의 판매자 신청 정보를 조회합니다. memberId 기준으로 조회하며, 본인 조회이므로 반려 사유(rejectReason)도 포함됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "C003 대상을 찾을 수 없습니다. (아직 판매자 신청을 한 적 없음)")
    })
    @GetMapping("/me")
    public ApiResponse<MySellerResponse> getMySeller() {
        return ApiResponse.ok(sellerService.getMySeller());
    }

    @Operation(
            summary = "판매자 조회",
            description = "판매자 ID로 상세 정보를 조회합니다. 인증 없이 누구나 조회 가능한 공개 API입니다. 계좌번호는 마스킹되어 내려갑니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "C003 대상을 찾을 수 없습니다.")
    })
    @SecurityRequirements
    @GetMapping("/{id}")
    public ApiResponse<SellerResponse> getSeller(
            @Parameter(description = "조회할 판매자 ID", example = "1") @PathVariable Long id) {
        return ApiResponse.ok(sellerService.getSeller(id));
    }

    @Operation(
            summary = "판매자 입점 신청 목록 조회 (admin)",
            description = "입점 신청 상태(applicationStatus)로 필터링된 판매자 목록을 조회합니다. 관리자 전용이며, 파라미터를 생략하면 PENDING 상태만 반환합니다. 관리자 전용 조회이므로 rejectReason도 포함됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "ME002 유효하지 않은 인증 토큰입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "SE007 권한이 없습니다.")
    })
    @GetMapping
    public ApiResponse<List<MySellerResponse>> getPendingSellers(
            @Parameter(description = "필터링할 입점 신청 상태. 생략 시 PENDING", example = "PENDING")
            @RequestParam(required = false, defaultValue = "PENDING") ApplicationStatus applicationStatus) {
        return ApiResponse.ok(sellerService.getPendingSellers(applicationStatus));
    }

}
