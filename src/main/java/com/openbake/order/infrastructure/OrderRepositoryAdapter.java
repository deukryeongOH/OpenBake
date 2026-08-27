package com.openbake.order.infrastructure;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.order.domain.Order;
import com.openbake.order.domain.OrderRepository;
import com.openbake.order.domain.OrderState;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryAdapter implements OrderRepository {
    private final OrderJpaRepository orderJpaRepository;

    /**
     * 결제에 넘길 orderId 가 필요해 즉시 INSERT 로 PK 를 확보한다.
     *
     * active_member_id UNIQUE 위반을 OR006 으로 바꾼다. 동시 요청이 둘 다 조회를
     * 통과한 뒤 INSERT 에서 갈리는 경우가 있어, 사전 조회만으로는 막히지 않는다.
     * DB 제약이 최종 방어선이고 여기가 그 예외를 도메인 언어로 옮기는 자리다.
     * (cart 의 CartRepositoryAdapter 가 같은 방식을 쓴다.)
     *
     * ⚠️ <b>진행 중 주문 슬롯 충돌만 OR006 으로 바꾼다.</b> 무결성 위반을 전부 삼키면
     * NOT NULL·FK 위반까지 "중복된 요청"으로 보고돼 원인을 찾을 수 없다.
     * 슬롯과 무관한 위반은 그대로 올려보내 500 과 스택트레이스로 드러낸다.
     */
    @Override
    public Order save(Order order) {
        try {
            return orderJpaRepository.saveAndFlush(order);
        } catch (DataIntegrityViolationException e) {
            if (isActiveMemberSlotConflict(e)) {
                throw new BusinessException(ErrorCode.DUPLICATE_REQUEST);
            }
            throw e;
        }
    }

    /**
     * 컬럼명으로 판단한다. 제약 이름은 환경마다 다르기 때문이다 —
     * 운영(PostgreSQL)은 마이그레이션이 지은 {@code uk_orders_active_member} 지만
     * 테스트(H2 + ddl-auto)는 Hibernate 가 만든 임의 이름이라 이름 매칭은 한쪽에서 깨진다.
     * 두 DB 모두 위반 메시지에 컬럼명을 담는다.
     */
    private static boolean isActiveMemberSlotConflict(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null
                && message.toLowerCase(Locale.ROOT).contains("active_member");
    }

    @Override
    public Optional<Order> findById(Long orderId) {
        return orderJpaRepository.findById(orderId);
    }

    @Override
    public Optional<Order> findByIdForUpdate(Long orderId) {
        return orderJpaRepository.findByIdForUpdate(orderId);
    }

    @Override
    public Optional<Order> findByItemId(Long orderItemId) {
        return orderJpaRepository.findByItemId(orderItemId);
    }

    @Override
    public Optional<Order> findByItemIdForUpdate(Long orderItemId) {
        return orderJpaRepository.findByItemIdForUpdate(orderItemId);
    }

    @Override
    public Optional<Order> findByActiveMemberId(Long memberId) {
        return orderJpaRepository.findByActiveMemberId(memberId);
    }

    @Override
    public Optional<Order> findByActiveMemberIdForUpdate(Long memberId) {
        return orderJpaRepository.findByActiveMemberIdForUpdate(memberId);
    }

    //살아 있다 = 아직 이 선점을 쓰고 있다. 종료된 주문(EXPIRED·FAILED·CANCELED)은 자리를 비켜 준다.
    private static final List<OrderState> LIVE_DROP_ORDER_STATES =
            List.of(OrderState.PENDING, OrderState.PAID);

    @Override
    public boolean existsLiveDropOrder(Long memberId, Long dropId) {
        return orderJpaRepository.existsLiveDropOrder(memberId, dropId, LIVE_DROP_ORDER_STATES);
    }

    @Override
    public Page<Order> findByMemberIdAndOrderStateInOrderByOrderIdDesc(
            Long memberId, List<OrderState> orderStates, Pageable pageable) {
        return orderJpaRepository.findByMemberIdAndOrderStateInOrderByOrderIdDesc(memberId, orderStates, pageable);
    }

    @Override
    public Page<Order> findBySellerId(Long sellerId, List<OrderState> orderStates, Pageable pageable) {
        return orderJpaRepository.findBySellerId(sellerId, orderStates, pageable);
    }

    @Override
    public List<Long> findAutoConfirmTargetItemIds(LocalDateTime paidAt) {
        return orderJpaRepository.findAutoConfirmTargetItemIds(paidAt);
    }

    @Override
    public List<Order> findExpiredPending(LocalDateTime now) {
        return orderJpaRepository.findExpiredPending(now);
    }

    @Override
    public List<Order> findLeakedActiveSlots() {
        return orderJpaRepository.findLeakedActiveSlots();
    }

    @Override
    public long countExpiredPending(LocalDateTime now) {
        return orderJpaRepository.countExpiredPending(now);
    }

    @Override
    public Optional<LocalDateTime> findOldestExpiredPendingAt(LocalDateTime now) {
        return orderJpaRepository.findOldestExpiredPendingAt(now);
    }

    @Override
    public long countLeakedActiveSlots() {
        return orderJpaRepository.countLeakedActiveSlots();
    }
}
