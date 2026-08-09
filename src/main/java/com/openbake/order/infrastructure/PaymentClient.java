package com.openbake.order.infrastructure;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;

@FeignClient(name = "payment-service", url = "${payment-service.url}")
public interface PaymentClient {

    @PostMapping("/internal/v1/payments/pay")
    PaymentResultResponse pay(@RequestBody PayRequest request);

    @PostMapping("/internal/v1/payments/refund")
    PaymentResultResponse refund(@RequestBody RefundRequest request);

    @PostMapping("/internal/v1/payments/confirm")
    PaymentResultResponse confirm(@RequestBody ConfirmRequest request);

    @GetMapping("/internal/v1/deposits/{memberId}/balance")
    BalanceResponse getBalance(@PathVariable("memberId") Long memberId);

    record PayRequest(String idempotencyKey, Long orderId, Long memberId, BigDecimal amount) {}
    record RefundRequest(String idempotencyKey, Long orderId) {}
    record ConfirmRequest(Long orderId) {}
    record PaymentResultResponse(String status, String message) {
        public boolean isSuccess() { return "SUCCESS".equals(status); }
    }
    record BalanceResponse(Long memberId, BigDecimal balance) {}
}
