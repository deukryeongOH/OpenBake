package com.openbake.payment.presentation.internal;

import com.openbake.payment.application.DepositService;
import com.openbake.payment.application.PaymentService;
import com.openbake.payment.application.dto.DepositResult;
import com.openbake.payment.application.dto.PaymentIdempotentResult;
import com.openbake.payment.presentation.internal.dto.BalanceResponse;
import com.openbake.payment.presentation.internal.dto.ConfirmRequest;
import com.openbake.payment.presentation.internal.dto.PayRequest;
import com.openbake.payment.presentation.internal.dto.PaymentResultResponse;
import com.openbake.payment.presentation.internal.dto.RefundRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class PaymentInternalController {

    private final PaymentService paymentService;
    private final DepositService depositService;

    @PostMapping("/payments/pay")
    public ResponseEntity<PaymentResultResponse> pay(@RequestBody PayRequest request) {
        PaymentIdempotentResult result = paymentService.payIdempotent(
                request.idempotencyKey(), request.orderId(), request.memberId(), request.amount());
        return ResponseEntity.ok(PaymentResultResponse.from(result));
    }

    @PostMapping("/payments/refund")
    public ResponseEntity<PaymentResultResponse> refund(@RequestBody RefundRequest request) {
        PaymentIdempotentResult result = paymentService.refundIdempotent(
                request.idempotencyKey(), request.orderId());
        return ResponseEntity.ok(PaymentResultResponse.from(result));
    }

    @PostMapping("/payments/confirm")
    public ResponseEntity<PaymentResultResponse> confirm(@RequestBody ConfirmRequest request) {
        paymentService.confirmPayment(request.orderId());
        return ResponseEntity.ok(PaymentResultResponse.success());
    }

    @GetMapping("/deposits/{memberId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable Long memberId) {
        DepositResult result = depositService.getBalance(memberId);
        return ResponseEntity.ok(BalanceResponse.from(result));
    }
}
