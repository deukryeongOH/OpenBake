package com.openbake.order.infrastructure;

import com.openbake.order.domain.Order;
import com.openbake.order.domain.OrderState;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.orderId = :orderId")
    Optional<Order> findByIdForUpdate(@Param("orderId") Long orderId);

    @Query("select i.order from OrderItem i where i.orderItemId = :orderItemId")
    Optional<Order> findByItemId(@Param("orderItemId") Long orderItemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i.order from OrderItem i where i.orderItemId = :orderItemId")
    Optional<Order> findByItemIdForUpdate(@Param("orderItemId") Long orderItemId);

    Optional<Order> findByActiveMemberId(Long memberId);

    /**
     * 이 회원이 같은 드롭으로 이미 살아 있는 주문을 갖고 있는가.
     *
     * <b>선점 하나로 주문을 두 번 만드는 것을 막는 근거다.</b> drop 의 선점은 결제 뒤에도
     * RESERVED 로 남아 있어 자격 검사만으로는 재사용을 걸러내지 못한다. "이 선점으로 이미
     * 주문을 만들었는가"는 drop 이 아니라 order 가 아는 사실이므로 여기서 답한다.
     */
    @Query("""
            select count(i) > 0 from OrderItem i
            where i.order.memberId = :memberId
              and i.dropId = :dropId
              and i.order.orderState in :liveStates
            """)
    boolean existsLiveDropOrder(@Param("memberId") Long memberId,
                                @Param("dropId") Long dropId,
                                @Param("liveStates") List<OrderState> liveStates);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.activeMemberId = :memberId")
    Optional<Order> findByActiveMemberIdForUpdate(@Param("memberId") Long memberId);

    Page<Order> findByMemberIdAndOrderStateInOrderByOrderIdDesc(
            Long memberId, List<OrderState> orderStates, Pageable pageable);

    /**
     * 판매자 판매내역. 항목에 sellerId 가 있으므로 조인해서 주문을 찾는다.
     *
     * distinct 를 붙이는 이유는 한 주문에 같은 판매자의 항목이 둘 이상일 수 있어서다
     * (같은 가게에서 빵 두 종류를 담은 경우). 조인만 하면 주문이 중복으로 나온다.
     */
    @Query("select distinct o from Order o join o.items i "
            + "where i.sellerId = :sellerId and o.orderState in :orderStates "
            + "order by o.orderId desc")
    Page<Order> findBySellerId(
            @Param("sellerId") Long sellerId,
            @Param("orderStates") List<OrderState> orderStates,
            Pageable pageable);

    @Query("select i.orderItemId from OrderItem i "
            + "where i.order.orderState = com.openbake.order.domain.OrderState.PAID "
            + "and i.order.paidAt < :paidAt "
            + "and i.itemStatus = com.openbake.order.domain.OrderItemStatus.UNCONFIRMED")
    List<Long> findAutoConfirmTargetItemIds(@Param("paidAt") LocalDateTime paidAt);

    @Query("select o from Order o "
            + "where o.orderState = com.openbake.order.domain.OrderState.PENDING "
            + "and o.reservationExpiresAt < :now")
    List<Order> findExpiredPending(@Param("now") LocalDateTime now);

    @Query("select o from Order o "
            + "where o.activeMemberId is not null "
            + "and o.orderState <> com.openbake.order.domain.OrderState.PENDING")
    List<Order> findLeakedActiveSlots();

    @Query("select count(o) from Order o "
            + "where o.orderState = com.openbake.order.domain.OrderState.PENDING "
            + "and o.reservationExpiresAt < :now")
    long countExpiredPending(@Param("now") LocalDateTime now);

    /**
     * 가장 오래 방치된 만료 PENDING의 예약 만료 시각. 건수만으로는 심각도를 알 수 없어
     * 나이를 함께 본다 — 한 건이라도 오래 남아 있으면 그 사용자의 돈이 계속 묶인다.
     */
    @Query("select min(o.reservationExpiresAt) from Order o "
            + "where o.orderState = com.openbake.order.domain.OrderState.PENDING "
            + "and o.reservationExpiresAt < :now")
    Optional<LocalDateTime> findOldestExpiredPendingAt(@Param("now") LocalDateTime now);

    @Query("select count(o) from Order o "
            + "where o.activeMemberId is not null "
            + "and o.orderState <> com.openbake.order.domain.OrderState.PENDING")
    long countLeakedActiveSlots();
}
