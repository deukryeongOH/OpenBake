package com.openbake.payment.presentation;

import com.openbake.common.response.ApiResponse;
import com.openbake.common.security.CurrentMemberProvider;
import com.openbake.payment.application.DepositService;
import com.openbake.payment.domain.TransactionType;
import com.openbake.payment.presentation.dto.DepositResponse;
import com.openbake.payment.presentation.dto.TransactionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/deposit")
@RequiredArgsConstructor
public class DepositController {

    private final DepositService depositService;
    private final CurrentMemberProvider currentMemberProvider;

    /**
     * 예치금 잔액 조회.
     */
    @GetMapping("/account")
    public ResponseEntity<ApiResponse<DepositResponse>> getBalance() {
        Long memberId = currentMemberProvider.getId();
        DepositResponse response = depositService.getBalance(memberId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * 거래 내역 조회 (5-2).
     */
    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getTransactions(
            @RequestParam(required = false) TransactionType transactionType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long memberId = currentMemberProvider.getId();
        Page<TransactionResponse> response = depositService.getTransactions(memberId, transactionType, page, size);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
