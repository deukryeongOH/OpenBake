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

    /**
     * 상태 전이용 행 잠금 조회.
     *
     * 결제 응답 반영과 취소·만료가 겹칠 수 있으므로, 실제 상태를 바꾸는 짧은 로컬
     * 트랜잭션에서만 사용한다. payment 원격 호출을 기다리는 동안에는 잠그지 않는다.
     */
    Optional<Order> findByIdForUpdate(Long orderId);

    //항목 단위 구매확정용. 판매자는 orderId 가 아니라 orderItemId 로 자기 항목을 확정한다.
    Optional<Order> findByItemId(Long orderItemId);

    Optional<Order> findByItemIdForUpdate(Long orderItemId);

    /**
     * 진행 중 주문 조회. 슬롯 컬럼을 그대로 본다.
     *
     * order_state = PENDING 으로 찾지 않는 이유는, 막는 근거와 조회하는 근거가
     * 같아야 하기 때문이다. UNIQUE 제약이 걸린 것은 이 컬럼이다.
     */
    Optional<Order> findByActiveMemberId(Long memberId);

    //새 주문 생성·드롭 우선권 전이와 결제 완료가 겹칠 때 쓰는 짧은 행 잠금 조회.
    Optional<Order> findByActiveMemberIdForUpdate(Long memberId);

    /**
     * 같은 드롭으로 이미 살아 있는(PENDING·PAID) 주문이 있는가.
     * 드롭 선점 하나로 주문이 여러 건 만들어지는 것을 막는다.
     */
    boolean existsLiveDropOrder(Long memberId, Long dropId);

    //본인 주문 목록(최신순). 상태 필터가 없는 경우 — 진행 중/미노출 상태는 서비스가 거른다.
    Page<Order> findByMemberIdAndOrderStateInOrderByOrderIdDesc(
            Long memberId, List<OrderState> orderStates, Pageable pageable);

    //판매자 본인 판매내역(최신순). Order.sellerId 가 사라져 항목 조인으로 찾는다.
    Page<Order> findBySellerId(Long sellerId, List<OrderState> orderStates, Pageable pageable);

    //자동 확정 배치용. 결제 완료 시각이 기준 이전인 PAID 주문의 미확정 항목 ID.
    List<Long> findAutoConfirmTargetItemIds(LocalDateTime paidAt);

    //만료 배치용. 예정 만료 시각이 지난 PENDING 주문.
    List<Order> findExpiredPending(LocalDateTime now);

    /**
     * 슬롯 누수 청소용. 종료 상태인데 슬롯이 남아 있는 주문.
     *
     * 0건이 아니면 그 자체가 전이 경로에 구멍이 있다는 알람이다.
     */
    List<Order> findLeakedActiveSlots();

    //아래 셋은 관측 전용이다. 엔티티를 적재하지 않도록 집계 쿼리로 분리한다.
    long countExpiredPending(LocalDateTime now);

    Optional<LocalDateTime> findOldestExpiredPendingAt(LocalDateTime now);

    long countLeakedActiveSlots();
}
