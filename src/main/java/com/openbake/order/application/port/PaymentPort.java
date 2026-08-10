package com.openbake.order.application.port;

import com.openbake.order.application.port.dto.BalanceInfo;
import com.openbake.order.application.port.dto.PaymentResult;

import java.math.BigDecimal;

public interface PaymentPort {

    PaymentResult pay(String idempotencyKey, Long orderId, Long memberId, BigDecimal amount);

    PaymentResult refund(String idempotencyKey, Long orderId);

    PaymentResult confirm(Long orderId);

    BalanceInfo getBalance(Long memberId);
}
