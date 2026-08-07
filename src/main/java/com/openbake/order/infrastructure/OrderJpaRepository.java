package com.openbake.order.infrastructure;

import com.openbake.order.domain.Order;
import com.openbake.order.domain.OrderState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {
    Page<Order> findByMemberIdOrderByOrderIdDesc(Long memberId, Pageable pageable);

    Page<Order> findByMemberIdAndOrderStateOrderByOrderIdDesc(Long memberId, OrderState orderState, Pageable pageable);

    Page<Order> findBySellerIdOrderByOrderIdDesc(Long sellerId, Pageable pageable);

    Page<Order> findBySellerIdAndOrderStateOrderByOrderIdDesc(Long sellerId, OrderState orderState, Pageable pageable);

    List<Order> findByOrderStateAndPaidAtBefore(OrderState orderState, LocalDateTime paidAt);
}
