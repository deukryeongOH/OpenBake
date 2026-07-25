package com.openbake.order.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    //본인 주문 목록(최신순). 상태 필터가 없는 경우.
    Page<Order> findByMemberIdOrderByOrderIdDesc(Long memberId, Pageable pageable);

    //본인 주문 목록(최신순). 상태 필터가 있는 경우.
    Page<Order> findByMemberIdAndOrderStateOrderByOrderIdDesc(Long memberId, OrderState orderState, Pageable pageable);
}
