package com.openbake.order.application;

import com.openbake.order.domain.Order;
import com.openbake.order.domain.OrderState;

import java.time.LocalDateTime;

public record OrderConfirmResult (
        Long orderId,
        OrderState orderState,
        LocalDateTime confirmedAt
) {

    public static OrderConfirmResult from(Order order) {
        return new OrderConfirmResult(
                order.getOrderId(),
                order.getOrderState(),
                order.getConfirmAt()
        );
    }
}