package com.openbake.payment.presentation;

import com.openbake.common.response.ApiResponse;
import com.openbake.common.security.CurrentMemberProvider;
import com.openbake.payment.application.ChargeFacade;
import com.openbake.payment.application.ChargeService;
import com.openbake.payment.application.dto.ChargeApproveCommand;
import com.openbake.payment.application.dto.ChargeApproveResult;
import com.openbake.payment.application.dto.ChargeCreateCommand;
import com.openbake.payment.application.dto.ChargeCreateResult;
import com.openbake.payment.application.dto.ChargeStatusResult;
import com.openbake.payment.application.dto.GetChargeStatusQuery;
import com.openbake.payment.presentation.dto.ChargeApproveRequest;
import com.openbake.payment.presentation.dto.ChargeApproveResponse;
import com.openbake.payment.presentation.dto.ChargeCreateRequest;
import com.openbake.payment.presentation.dto.ChargeCreateResponse;
import com.openbake.payment.presentation.dto.ChargeStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Charge", description = "예치금 충전 요청 / 승인 / 상태 조회")
@RestController
@RequestMapping("/api/v1/deposit/charges")
@RequiredArgsConstructor
public class ChargeController {

    private final ChargeService chargeService;
    private final ChargeFacade chargeFacade;
    private final CurrentMemberProvider currentMemberProvider;

    @Operation(
            summary = "충전 요청 생성",
            description = "PG 결제창을 띄우기 위한 충전 요청을 생성합니다. 프론트는 응답의 pgOrderId와 amount로 토스페이먼츠 결제창을 띄웁니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "P001 충전 금액이 올바르지 않습니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "ME002 유효하지 않은 인증 토큰입니다.")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<ChargeCreateResponse>> createCharge(
            @RequestBody ChargeCreateRequest request) {
        Long memberId = currentMemberProvider.getId();
        ChargeCreateResult result = chargeService.createChargeRequest(
                new ChargeCreateCommand(memberId, request.amount()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(ChargeCreateResponse.from(result)));
    }

    @Operation(
            summary = "충전 상태 조회",
            description = "충전 요청 1건의 현재 상태를 조회합니다. PG_TIMEOUT(504) 이후 프론트가 5초 간격으로 폴링하거나, 충전 내역에서 상태를 확인할 때 사용합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "ME002 유효하지 않은 인증 토큰입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "P003 본인의 충전 요청이 아닙니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "P002 존재하지 않는 충전 요청입니다.")
    })
    @GetMapping("/{chargeRequestId}")
    public ResponseEntity<ApiResponse<ChargeStatusResponse>> getChargeStatus(
            @Parameter(description = "충전 요청 ID", example = "1")
            @PathVariable Long chargeRequestId) {
        Long memberId = currentMemberProvider.getId();
        ChargeStatusResult result = chargeService.getChargeStatus(
                new GetChargeStatusQuery(chargeRequestId, memberId));
        return ResponseEntity.ok(ApiResponse.ok(ChargeStatusResponse.from(result)));
    }

    @Operation(
            summary = "충전 승인",
            description = "토스 결제창 완료 후 paymentKey, orderId, amount를 보내면 서버가 PG 승인 API를 호출하고 예치금을 증가시킵니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "P004 충전 금액이 일치하지 않습니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "ME002 유효하지 않은 인증 토큰입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "P003 본인의 충전 요청이 아닙니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "P002 존재하지 않는 충전 요청입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "P005 승인할 수 없는 충전 상태 / P006 만료된 충전 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "P007 결제 승인에 실패했습니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "504", description = "P008 결제 결과를 확인 중입니다. 잠시 후 내역을 확인해주세요.")
    })
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<ChargeApproveResponse>> approveCharge(
            @RequestBody ChargeApproveRequest request) {
        Long memberId = currentMemberProvider.getId();
        ChargeApproveResult result = chargeFacade.approve(
                new ChargeApproveCommand(memberId, request.paymentKey(),
                        request.orderId(), request.amount()));
        return ResponseEntity.ok(ApiResponse.ok(ChargeApproveResponse.from(result)));
    }
}
