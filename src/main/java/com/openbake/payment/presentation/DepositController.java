package com.openbake.payment.presentation;

import com.openbake.common.response.ApiResponse;
import com.openbake.common.security.CurrentMemberProvider;
import com.openbake.payment.application.DepositService;
import com.openbake.payment.domain.TransactionType;
import com.openbake.payment.presentation.dto.DepositResponse;
import com.openbake.payment.presentation.dto.TransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Deposit", description = "예치금 잔액 조회 / 거래 내역 조회")
@RestController
@RequestMapping("/api/v1/deposit")
@RequiredArgsConstructor
public class DepositController {

    private final DepositService depositService;
    private final CurrentMemberProvider currentMemberProvider;

    @Operation(
            summary = "예치금 잔액 조회",
            description = "로그인한 회원의 예치금 잔액을 조회합니다. 계좌가 없으면 자동 생성됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "ME002 유효하지 않은 인증 토큰입니다.")
    })
    @GetMapping("/account")
    public ResponseEntity<ApiResponse<DepositResponse>> getBalance() {
        Long memberId = currentMemberProvider.getId();
        DepositResponse response = depositService.getBalance(memberId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(
            summary = "거래 내역 조회",
            description = "예치금 거래 내역을 최신순으로 페이징 조회합니다. 충전/결제/환불 이력이 포함됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "C001 잘못된 요청입니다. / P009 유효하지 않은 거래 유형입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "ME002 유효하지 않은 인증 토큰입니다.")
    })
    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getTransactions(
            @Parameter(description = "거래 유형 필터 (미지정 시 전체)", example = "CHARGE")
            @RequestParam(required = false) TransactionType transactionType,
            @Parameter(description = "페이지 번호", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 (최대 50)", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        Long memberId = currentMemberProvider.getId();
        Page<TransactionResponse> response = depositService.getTransactions(memberId, transactionType, page, size);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
