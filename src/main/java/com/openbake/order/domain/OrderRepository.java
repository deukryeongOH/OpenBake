package com.openbake.order.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    //본인 주문 목록(최신순). 상태 필터가 없는 경우.
    Page<Order> findByMemberIdOrderByOrderIdDesc(Long memberId, Pageable pageable);

    //본인 주문 목록(최신순). 상태 필터가 있는 경우.
    Page<Order> findByMemberIdAndOrderStateOrderByOrderIdDesc(Long memberId, OrderState orderState, Pageable pageable);

    //자동 확정 배치용. 결제 완료 시각이 기준 이전인 특정 상태(PAID) 주문.
    List<Order> findByOrderStateAndPaidAtBefore(OrderState orderState, LocalDateTime paidAt);
}
