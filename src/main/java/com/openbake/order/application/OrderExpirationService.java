package com.openbake.order.application;

import com.openbake.order.application.port.PaymentPort;
import com.openbake.order.application.port.dto.PaymentResult;
import com.openbake.order.domain.Order;
import com.openbake.order.domain.OrderFailReason;
import com.openbake.order.domain.OrderRepository;
import com.openbake.order.domain.OrderState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 만료 처리.
 *
 * 사용자가 주문서를 띄워둔 채 결제하지 않으면 주문이 PENDING 으로 남는다.
 * <b>드롭은 lock-start 에서 이미 재고가 깎였으므로</b> 이 시간만큼 재고가 묶이고,
 * 이걸 되돌릴 안전망이 지금 이 배치뿐이다(drop 에는 선점 회수 배치가 없다).
 *
 * 일반 상품은 재고 차감을 결제 뒤로 옮겼으므로 실패·만료 때 되돌릴 것이 없다.
 * 결제 시도 이력이 있으면 먼저 Payment 결과를 조회하고, 뒤늦은 SUCCESS라면 재고 차감과
 * PAID 전이를 이어서 처리한다.
 *
 * 이 클래스에도 @Transactional 이 클래스 단위로 없다 —
 * payment 조회·환불은 Feign 이라 DB 트랜잭션 밖에서 해야 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderExpirationService {

    private final OrderRepository orderRepository;
    private final PaymentPort paymentPort;
    private final PaymentResultQueryService paymentResultQueryService;
    private final OrderPayService payService;
    private final OrderPayTransactions payTransactions;
    private final OrderExpirationTransactions tx;

    @Transactional(readOnly = true)
    public List<Long> findExpiredPendingIds() {
        return orderRepository.findExpiredPending(LocalDateTime.now()).stream()
                .map(Order::getOrderId)
                .toList();
    }

    /**
     * 만료 후보 한 건 처리. 스케줄러가 주문별로 부른다(주문별 트랜잭션).
     *
     * <b>가장 위험한 구간은 결제 응답 타임아웃과 겹칠 때다.</b> 결제는 성공했는데
     * order 가 아직 PENDING 이면, 재고를 먼저 되돌리는 순간 돈만 빠지고 물건은 없어진다.
     * 그래서 payAttemptedAt 이 있으면 <b>반드시 결과를 먼저 조회한다.</b>
     */
    public void expire(Long orderId) {
        Order order = payTransactions.load(orderId);
        //조회 후 처리 사이에 결제가 성공했을 수 있다. PENDING 이 아니면 건너뛴다(멱등).
        if (order.getOrderState() != OrderState.PENDING) {
            return;
        }

        if (order.getPayAttemptedAt() == null) {
            //결제를 시도한 적이 없다. 되돌릴 결제가 없으므로 그냥 닫는다.
            tx.restoreAndExpire(orderId);
            return;
        }

        resolveUnknownPayment(order);
    }

    /**
     * 결제를 시도했는데 결과를 모르는 주문을 확정한다.
     *
     * <b>배치가 pay 를 재호출해서는 안 된다.</b> payIdempotent 는 기록이 없으면 그 자리에서
     * 실제 결제를 수행한다 — 사용자가 떠난 지 15분 뒤 없던 결제가 생긴다.
     * 배치가 쓰는 것은 결과 조회와 보상용 refund 뿐이다.
     */
    private void resolveUnknownPayment(Order order) {
        Long orderId = order.getOrderId();
        int attemptNo = order.getPayAttemptNo();

        PaymentResult result;
        try {
            result = paymentResultQueryService.query(order.currentPaymentIdempotencyKey());
        } catch (RuntimeException e) {
            //payment 가 죽어 있으면 결과도 보상도 확정할 수 없다. 다음 주기에 다시 본다.
            //돈의 결과를 모르는 상태에서 슬롯을 먼저 반납하지 않는다.
            log.warn("결제 결과 조회 실패 — PENDING 유지, 다음 주기 재시도. orderId={}, reason={}",
                    orderId, e.toString());
            return;
        }

        if (result.isSuccess()) {
            //결제는 성공했다. 주문을 살린다 — 재고 차감은 아직 안 됐으므로 여기서 한다.
            log.info("타임아웃 이후 결제 성공 확인 — 주문을 PAID 로 복원한다. orderId={}", orderId);
            payService.applyPaymentSuccess(order.getMemberId(), orderId, order.getTotalAmount());
            return;
        }

        if (result.isFail()) {
            tx.restoreAndFailIfCurrentAttempt(
                    orderId, attemptNo, OrderFailReason.PAYMENT_FAILED);
            return;
        }

        //NOT_FOUND 인 채로 만료에 도달했다. 멱등 환불을 마지막 안전장치로 쓴다.
        closeWithSafetyRefund(order, attemptNo);
    }

    /**
     * 환불을 마지막 안전장치로 호출한 뒤 주문을 닫는다.
     *
     * 조회가 SUCCESS 면 여기 오지 않는다 — 성공한 결제를 굳이 환불해 사용자에게
     * 손해를 끼칠 이유가 없다. 계속 NOT_FOUND 인 경우에만 온다.
     * 환불이 실패하면 주문을 닫지 않고 다음 재시도·알림 대상으로 남긴다.
     */
    private void closeWithSafetyRefund(Order order, int expectedAttemptNo) {
        Long orderId = order.getOrderId();
        if (!tx.isCurrentPendingAttempt(orderId, expectedAttemptNo)) {
            //조회 중 다음 사용자 결제가 시작됐다. 다음 배치에서 새 키를 조회한다.
            return;
        }

        PaymentResult refund;
        try {
            refund = paymentPort.refund(
                    "order-" + orderId + "-refund",
                    orderId,
                    order.getMemberId(),
                    order.getTotalAmount());
        } catch (RuntimeException e) {
            log.warn("보상 환불 실패 — PENDING 유지, 다음 주기 재시도. orderId={}, reason={}",
                    orderId, e.toString());
            return;
        }

        if (!refund.isSuccess()) {
            log.error("보상 환불이 성공하지 않았다 — PENDING 유지, 수동 확인 대상. orderId={}, status={}",
                    orderId, refund.status());
            return;
        }

        tx.restoreAndFailIfCurrentAttempt(
                orderId, expectedAttemptNo, OrderFailReason.PAYMENT_UNKNOWN);
    }

    /**
     * 슬롯 누수 청소.
     *
     * 종료 전이 중 한 군데라도 슬롯 반납을 빠뜨리면 그 회원은 <b>영구히 주문을 못 한다.</b>
     * 15분 만료는 PENDING 만 보므로 이 경우를 구제하지 못한다.
     *
     * 0건이 아니면 그 자체가 전이 경로에 구멍이 있다는 알람이다.
     */
    public int releaseLeakedSlots() {
        return tx.releaseLeakedSlots();
    }
}
