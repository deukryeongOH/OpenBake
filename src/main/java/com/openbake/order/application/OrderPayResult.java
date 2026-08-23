package com.openbake.order.application;

import com.openbake.order.domain.OrderState;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 결제 요청 결과.
 *
 * outcome 이 별도로 있는 이유는 <b>타임아웃이 실패가 아니기 때문</b>이다.
 * PROCESSING 은 결제나 보상 결과를 아직 확정하지 못했다는 뜻이라,
 * 성공도 실패도 아닌 제3의 응답으로 프론트에 알려야 한다.
 */
public record OrderPayResult(
        Long orderId,
        OrderState orderState,
        Outcome outcome,
        BigDecimal totalAmount,
        //결제 성공 또는 확정 실패 시 채울 수 있다. 잔액 조회 실패·처리중에는 null이다.
        BigDecimal balanceAfter,
        LocalDateTime paidAt,
        String message
) {

    public enum Outcome {
        PAID,
        //잔액 부족 등 payment 의 업무 실패. 주문은 PENDING이라 충전 후 재결제할 수 있다.
        PAYMENT_FAILED,
        //결제는 됐는데 재고가 없어 환불로 되돌림.
        OUT_OF_STOCK,
        //결제 응답 전에 취소·만료가 끝나 결제를 환불로 되돌림.
        PAYMENT_REVERSED,
        //타임아웃 또는 보상 미확정. 일반적으로 PENDING에서 결과를 조회 중이다.
        PROCESSING
    }

    public static OrderPayResult processing(Long orderId, BigDecimal totalAmount) {
        return new OrderPayResult(orderId, OrderState.PENDING, Outcome.PROCESSING,
                totalAmount, null, null, null);
    }

    public static OrderPayResult paymentFailed(
            Long orderId,
            OrderState orderState,
            BigDecimal totalAmount,
            BigDecimal balanceAfter,
            String message) {
        return new OrderPayResult(orderId, orderState, Outcome.PAYMENT_FAILED,
                totalAmount, balanceAfter, null, message);
    }

    public static OrderPayResult paymentReversed(Long orderId, OrderState orderState, BigDecimal totalAmount) {
        return new OrderPayResult(orderId, orderState, Outcome.PAYMENT_REVERSED,
                totalAmount, null, null, null);
    }
}
