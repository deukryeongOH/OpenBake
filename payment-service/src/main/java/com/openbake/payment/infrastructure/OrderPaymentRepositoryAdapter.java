package com.openbake.payment.infrastructure;

import com.openbake.payment.domain.OrderPayment;
import com.openbake.payment.domain.OrderPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OrderPaymentRepositoryAdapter implements OrderPaymentRepository {

    private final OrderPaymentJpaRepository jpaRepository;

    @Override
    public OrderPayment save(OrderPayment orderPayment) {
        return jpaRepository.save(orderPayment);
    }

    @Override
    public Optional<OrderPayment> findByOrderId(Long orderId) {
        return jpaRepository.findByOrderId(orderId);
    }
}
