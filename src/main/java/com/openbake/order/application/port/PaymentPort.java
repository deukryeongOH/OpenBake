package com.openbake.order.application.port;

import com.openbake.order.application.port.dto.BalanceInfo;
import com.openbake.order.application.port.dto.PaymentResult;

import java.math.BigDecimal;

public interface PaymentPort {

    /**
     * 예치금 차감 요청.
     *
     * 차감 멱등키는 Order가 현재 시도 번호로 생성해 전달한다.
     */
    PaymentResult pay(String idempotencyKey, Long orderId, Long memberId, BigDecimal amount);

    PaymentResult refund(String idempotencyKey, Long orderId, Long memberId, BigDecimal amount);

    PaymentResult confirm(Long orderId);

    BalanceInfo getBalance(Long memberId);

    /**
     * 결제 결과 조회. <b>타임아웃은 실패가 아니라 "모름"</b>이라 결과를 물어봐야 한다.
     *
     * 부작용이 없는 GET 이라 Retry 를 걸어도 안전하다. 반대로 pay 는 자동 재호출하지 않는다.
     *
     * SUCCESS / FAIL / NOT_FOUND 를 돌려준다.
     */
    PaymentResult getPayResult(String idempotencyKey);
}
