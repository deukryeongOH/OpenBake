package com.openbake.payment.presentation;

import com.openbake.common.response.ApiResponse;
import com.openbake.common.security.CurrentMemberProvider;
import com.openbake.payment.application.ChargeFacade;
import com.openbake.payment.application.ChargeService;
import com.openbake.payment.presentation.dto.ChargeApproveRequest;
import com.openbake.payment.presentation.dto.ChargeApproveResponse;
import com.openbake.payment.presentation.dto.ChargeCreateRequest;
import com.openbake.payment.presentation.dto.ChargeCreateResponse;
import com.openbake.payment.presentation.dto.ChargeStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/deposit/charges")
@RequiredArgsConstructor
public class ChargeController {

    private final ChargeService chargeService;
    private final ChargeFacade chargeFacade;
    private final CurrentMemberProvider currentMemberProvider;

    /**
     * 충전 요청 생성.
     * 프론트는 이 응답의 pgOrderId와 amount로 토스페이먼츠 결제창을 띄운다.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ChargeCreateResponse>> createCharge(
            @RequestBody ChargeCreateRequest request) {
        Long memberId = currentMemberProvider.getId();
        ChargeCreateResponse response = chargeService.createChargeRequest(
                memberId, request.amount());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    /**
     * 충전 상태 조회 (5-5).
     * PG_TIMEOUT(504) 이후 프론트가 5초 간격으로 폴링하거나,
     * 충전 내역에서 상태를 확인할 때 사용.
     */
    @GetMapping("/{chargeRequestId}")
    public ResponseEntity<ApiResponse<ChargeStatusResponse>> getChargeStatus(
            @PathVariable Long chargeRequestId) {
        Long memberId = currentMemberProvider.getId();
        ChargeStatusResponse response = chargeService.getChargeStatus(chargeRequestId, memberId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * 충전 승인.
     * 프론트가 토스 결제창 완료 후 paymentKey, orderId, amount를 보내면
     * 서버가 PG 승인 API를 호출하고 예치금을 증가시킨다.
     */
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<ChargeApproveResponse>> approveCharge(
            @RequestBody ChargeApproveRequest request) {
        Long memberId = currentMemberProvider.getId();
        ChargeApproveResponse response = chargeFacade.approve(
                memberId, request.paymentKey(),
                request.orderId(), request.amount());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
