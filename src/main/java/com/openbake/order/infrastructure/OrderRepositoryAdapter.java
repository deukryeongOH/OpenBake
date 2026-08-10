package com.openbake.order.infrastructure;

import com.openbake.order.domain.Order;
import com.openbake.order.domain.OrderRepository;
import com.openbake.order.domain.OrderState;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryAdapter implements OrderRepository {
    private final OrderJpaRepository orderJpaRepository;

    //결제에 넘길 orderId 가 필요해 즉시 INSERT 로 PK 를 확보한다.
    @Override
    public Order save(Order order) {
        return orderJpaRepository.saveAndFlush(order);
    }

    @Override
    public Optional<Order> findById(Long orderId) {
        return orderJpaRepository.findById(orderId);
    }

    @Override
    public Page<Order> findByMemberIdOrderByOrderIdDesc(Long memberId, Pageable pageable) {
        return orderJpaRepository.findByMemberIdOrderByOrderIdDesc(memberId, pageable);
    }

    @Override
    public Page<Order> findByMemberIdAndOrderStateOrderByOrderIdDesc(Long memberId, OrderState orderState, Pageable pageable) {
        return orderJpaRepository.findByMemberIdAndOrderStateOrderByOrderIdDesc(memberId, orderState, pageable);
    }

    @Override
    public Page<Order> findBySellerIdOrderByOrderIdDesc(Long sellerId, Pageable pageable) {
        return orderJpaRepository.findBySellerIdOrderByOrderIdDesc(sellerId, pageable);
    }

    @Override
    public Page<Order> findBySellerIdAndOrderStateOrderByOrderIdDesc(Long sellerId, OrderState orderState, Pageable pageable) {
        return orderJpaRepository.findBySellerIdAndOrderStateOrderByOrderIdDesc(sellerId, orderState, pageable);
    }

    @Override
    public List<Order> findByOrderStateAndPaidAtBefore(OrderState orderState, LocalDateTime paidAt) {
        return orderJpaRepository.findByOrderStateAndPaidAtBefore(orderState, paidAt);
    }

}
