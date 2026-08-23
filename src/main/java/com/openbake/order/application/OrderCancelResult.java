package com.openbake.order.application;

import com.openbake.order.domain.OrderState;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 취소 결과. 하나의 API 가 상태로 갈리므로 결과도 두 모양을 겸한다.
 *
 * <pre>
 * PENDING → EXPIRED   환불 없음(refundAmount = 0), 주문 내역 미노출
 * PAID    → CANCELED  전액 환불, 주문 내역 노출
 * </pre>
 *
 * 프론트는 orderState 로 어느 쪽인지 안다.
 */
public record OrderCancelResult(
        Long orderId,
        OrderState orderState,
        //결제 전 취소면 0. 실제로 돈이 오간 경우에만 값이 있다.
        BigDecimal refundAmount,
        //환불이 있었을 때만 조회한다. 결제 전 취소면 null.
        BigDecimal balanceAfter,
        LocalDateTime endedAt
) {
}
