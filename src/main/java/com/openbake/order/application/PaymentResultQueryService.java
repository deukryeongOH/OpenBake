package com.openbake.order.application;

import com.openbake.order.application.port.PaymentPort;
import com.openbake.order.application.port.dto.PaymentResult;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 결제 결과 조회 전용 경계.
 *
 * 상태 변경인 pay에는 자동 재시도를 걸지 않고, 부작용 없는 결과 조회만 짧게 재시도한다.
 */
@Service
@RequiredArgsConstructor
public class PaymentResultQueryService {

    private final PaymentPort paymentPort;

    @Retry(name = "paymentResultQuery")
    public PaymentResult query(String idempotencyKey) {
        return paymentPort.getPayResult(idempotencyKey);
    }
}
