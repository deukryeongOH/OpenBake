package com.openbake.payment.domain;

import java.util.Optional;

public interface OrderPaymentRepository {
    OrderPayment save(OrderPayment orderPayment);
    Optional<OrderPayment> findByOrderId(Long orderId);
}
