package com.openbake.order.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderOutboxEventRepository extends JpaRepository<OrderOutboxEvent, Long> {

    //릴레이 폴링용. PENDING 을 생성 순서대로 일정 개수 가져온다.
    List<OrderOutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
