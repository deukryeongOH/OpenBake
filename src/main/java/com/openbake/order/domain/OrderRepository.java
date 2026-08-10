package com.openbake.order.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    //결제에 넘길 orderId 가 필요하므로 저장 즉시 PK 를 확보한다.
    Order save(Order order);

    Optional<Order> findById(Long orderId);

    //본인 주문 목록(최신순). 상태 필터가 없는 경우.
    Page<Order> findByMemberIdOrderByOrderIdDesc(Long memberId, Pageable pageable);

    //본인 주문 목록(최신순). 상태 필터가 있는 경우.
    Page<Order> findByMemberIdAndOrderStateOrderByOrderIdDesc(Long memberId, OrderState orderState, Pageable pageable);

    //판매자 본인 판매내역(최신순). 상태 필터가 없는 경우.
    Page<Order> findBySellerIdOrderByOrderIdDesc(Long sellerId, Pageable pageable);

    //판매자 본인 판매내역(최신순). 상태 필터가 있는 경우.
    Page<Order> findBySellerIdAndOrderStateOrderByOrderIdDesc(Long sellerId, OrderState orderState, Pageable pageable);

    //자동 확정 배치용. 결제 완료 시각이 기준 이전인 특정 상태(PAID) 주문.
    List<Order> findByOrderStateAndPaidAtBefore(OrderState orderState, LocalDateTime paidAt);
}
