package com.openbake.order.application;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.order.domain.Order;
import com.openbake.order.domain.OrderFailReason;
import com.openbake.order.domain.OrderRepository;
import com.openbake.order.domain.OrderState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 만료 배치의 DB 트랜잭션 조각들.
 *
 * payment 조회·환불(Feign)이 트랜잭션 밖에 있어야 해서 {@link OrderExpirationService}
 * 에서 분리했다. 같은 클래스 안에서 부르면 프록시를 타지 않는다.
 *
 * <b>모든 전이가 조건부다.</b> 결제가 방금 성공해 PAID 로 바뀌는 것과 배치가 겹칠 수 있고,
 * 그때 재고를 되돌리면 산 물건의 재고가 남에게 돌아간다.
 * 복구 메서드들은 중복 호출을 조용히 넘기지 않고 예외를 던지므로
 * (rollbackStock 은 INVALID_TOTAL_QUANTITY, drop 은 NOT_RESERVED_STATUS)
 * <b>상태 전이로 막는 것이 유일한 방어다.</b>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpirationTransactions {

    private final OrderRepository orderRepository;
    private final OrderStockRestorer stockRestorer;

    /**
     * ⚠️ 드롭 선점 복구는 재고 벌크 UPDATE({@code clearAutomatically = true})를 타므로
     * 실행 즉시 영속성 컨텍스트가 비워지고 위에서 읽은 order 가 준영속이 된다.
     * 복구 뒤에 <b>다시 읽어야</b> 상태 전이가 저장된다 —
     * 그러지 않으면 예외 없이 조용히 유실되어 주문이 PENDING 에 남는다.
     */
    @Transactional
    public void restoreAndExpire(Long orderId) {
        Order order = pendingOrSkip(orderId);
        if (order == null) {
            return;
        }
        stockRestorer.restorePending(order);
        pendingOrSkip(orderId).markExpired();
    }

    @Transactional
    public boolean restoreAndFailIfCurrentAttempt(
            Long orderId, int expectedAttemptNo, OrderFailReason reason) {
        Order order = currentPendingAttemptOrSkip(orderId, expectedAttemptNo);
        if (order == null) {
            return false;
        }
        stockRestorer.restorePending(order);
        //복구로 컨텍스트가 비워졌을 수 있으므로 다시 읽어 전이한다.
        currentPendingAttemptOrSkip(orderId, expectedAttemptNo).markFailed(reason);
        return true;
    }

    /** 외부 안전 환불 전에 조회한 결제 시도 번호가 아직 유효한지 잠금으로 확인한다. */
    @Transactional
    public boolean isCurrentPendingAttempt(Long orderId, int expectedAttemptNo) {
        return currentPendingAttemptOrSkip(orderId, expectedAttemptNo) != null;
    }

    @Transactional
    public int releaseLeakedSlots() {
        List<Order> leaked = orderRepository.findLeakedActiveSlots();
        leaked.forEach(order -> {
            log.error("진행 중 주문 슬롯 누수 — 자동 반납한다. orderId={}, state={}",
                    order.getOrderId(), order.getOrderState());
            order.releaseLeakedSlot();
        });
        return leaked.size();
    }

    //조회와 전이 사이에 상태가 바뀌었으면 아무것도 하지 않는다.
    private Order pendingOrSkip(Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        return order.getOrderState() == OrderState.PENDING ? order : null;
    }

    private Order currentPendingAttemptOrSkip(Long orderId, int expectedAttemptNo) {
        Order order = pendingOrSkip(orderId);
        if (order == null || order.getPayAttemptNo() != expectedAttemptNo) {
            return null;
        }
        return order;
    }
}
