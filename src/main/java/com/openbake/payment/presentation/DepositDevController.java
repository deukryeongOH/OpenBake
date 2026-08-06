package com.openbake.payment.presentation;

import com.openbake.common.response.ApiResponse;
import com.openbake.common.security.CurrentMemberProvider;
import com.openbake.payment.application.DepositDevService;
import com.openbake.payment.application.dto.DepositResult;
import com.openbake.payment.application.dto.DevChargeCommand;
import com.openbake.payment.presentation.dto.DepositResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// [DEV 전용] PG 없이 예치금 직접 충전. 운영(prod) 프로파일에서는 빈 자체가 등록되지 않아 API가 존재하지 않음.
@Tag(name = "Deposit", description = "예치금 잔액 조회 / 거래 내역 조회")
@Profile({"local", "dev"})
@RestController
@RequestMapping("/api/v1/deposit/dev")
@RequiredArgsConstructor
public class DepositDevController {

    private final DepositDevService depositDevService;
    private final CurrentMemberProvider currentMemberProvider;

    @Operation(
            summary = "[DEV 전용] PG 없이 예치금 직접 충전",
            description = "PG 결제 과정 없이 로그인한 회원의 예치금을 직접 충전합니다. local/dev 프로파일에서만 노출됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "금액은 0보다 커야 합니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "ME002 유효하지 않은 인증 토큰입니다.")
    })
    @PostMapping("/charge")
    public ResponseEntity<ApiResponse<DepositResponse>> devCharge(
            @RequestBody DevChargeRequest request) {
        Long memberId = currentMemberProvider.getId();
        DepositResult result = depositDevService.devCharge(
                new DevChargeCommand(memberId, request.amount()));
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.ok(DepositResponse.from(result)));
    }

    public record DevChargeRequest(
            @io.swagger.v3.oas.annotations.media.Schema(description = "충전 금액", example = "50000")
            java.math.BigDecimal amount
    ) {}
}
