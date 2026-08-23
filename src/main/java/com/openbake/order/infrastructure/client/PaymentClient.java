package com.openbake.order.infrastructure.client;

import com.openbake.order.application.port.PaymentPort;
import com.openbake.order.application.port.dto.BalanceInfo;
import com.openbake.order.application.port.dto.PaymentResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;

@FeignClient(name = "payment-service", url = "${payment-service.url}")
public interface PaymentClient extends PaymentPort {

    @PostMapping("/internal/v1/payments/pay")
    PaymentResult pay(@RequestBody PayRequest request);

    @PostMapping("/internal/v1/payments/refund")
    PaymentResult refund(@RequestBody RefundRequest request);

    @PostMapping("/internal/v1/payments/confirm")
    PaymentResult confirm(@RequestBody ConfirmRequest request);

    @Override
    @GetMapping("/internal/v1/deposits/{memberId}/balance")
    BalanceInfo getBalance(@PathVariable("memberId") Long memberId);

    /**
     * 결제 결과 조회. 타임아웃 이후 결과를 확정하기 위한 부작용 없는 GET.
     *
     * payment 는 Order가 보낸 차감 멱등키의 SUCCESS / FAIL / NOT_FOUND 를 돌려준다.
     * 계약 상세는 .claude/docs/payment-timeout-issue.md 에 있다.
     */
    @Override
    @GetMapping("/internal/v1/payments/pay-result/{idempotencyKey}")
    PaymentResult getPayResult(@PathVariable("idempotencyKey") String idempotencyKey);

    // ── PaymentPort 구현: 도메인 시그니처 → Feign 요청 변환 ──

    @Override
    default PaymentResult pay(String idempotencyKey, Long orderId, Long memberId, BigDecimal amount) {
        return pay(new PayRequest(idempotencyKey, orderId, memberId, amount));
    }

    @Override
    default PaymentResult refund(
            String idempotencyKey, Long orderId, Long memberId, BigDecimal amount) {
        return refund(new RefundRequest(idempotencyKey, orderId, memberId, amount));
    }

    @Override
    default PaymentResult confirm(Long orderId) {
        return confirm(new ConfirmRequest(orderId));
    }

    // ── Feign 전용 요청 DTO ──

    record PayRequest(String idempotencyKey, Long orderId, Long memberId, BigDecimal amount) {}
    record RefundRequest(String idempotencyKey, Long orderId, Long memberId, BigDecimal amount) {}
    record ConfirmRequest(Long orderId) {}
}
