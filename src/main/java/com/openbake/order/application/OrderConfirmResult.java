package com.openbake.order.application;

import com.openbake.order.domain.OrderItemStatus;

import java.time.LocalDateTime;

/**
 * 구매확정 결과. 주문 전체 상태가 아니라 확정한 항목의 상태를 반환한다.
 */
public record OrderConfirmResult(
        Long orderId,
        Long orderItemId,
        OrderItemStatus itemStatus,
        LocalDateTime confirmedAt
) {
}
