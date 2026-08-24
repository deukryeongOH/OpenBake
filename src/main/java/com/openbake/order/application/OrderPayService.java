package com.openbake.order.application;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.order.application.OrderPayTransactions.PaymentApplyResult;
import com.openbake.order.application.OrderPayTransactions.PayPreparation;
import com.openbake.order.application.port.PaymentPort;
import com.openbake.order.application.port.dto.PaymentResult;
import com.openbake.order.domain.Order;
import com.openbake.order.domain.OrderFailReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 결제. <b>order 가 조율자인 Orchestration Saga</b>의 본체다.
 *
 * 이 클래스에는 @Transactional 이 없다. payment 는 별도 프로세스라 결제가 order
 * 트랜잭션 밖에서 커밋되고, 그래서 외부 호출을 트랜잭션 안에 둘 수 없다.
 * DB 작업은 {@link OrderPayTransactions} 의 짧은 로컬 트랜잭션으로 끊어 실행한다.
 *
 * <pre>
 * T2  시도 번호·멱등키·payAttemptedAt 커밋
 * ──  payment pay 동기 호출     ← 트랜잭션 밖
 * T4  결과 반영                ← SUCCESS는 PAID, FAIL은 PENDING 유지
 * </pre>
 *
 * 주문 결제는 PG 결제창이 아니라 <b>예치금 차감</b>이다. 토스는 충전 경로에만 있고
 * 주문 결제 경로에는 없다. 그래서 "결제창을 닫아 재고가 유령으로 남는" 문제는 없고,
 * 대신 사용자가 <b>주문서 화면에 머무는 구간</b>이 그 자리를 대신한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPayService {

    private final OrderPayTransactions tx;
    private final PaymentPort paymentPort;
    private final PaymentResultQueryService paymentResultQueryService;

    /**
     * 결제 요청.
     *
     * 실패가 두 종류라는 것이 핵심이다.
     * <ul>
     *   <li>업무 실패(잔액 부족 등) — payment 가 <b>200 + status=FAIL</b> 로 준다. 예외가 아니다</li>
     *   <li>타임아웃·연결 실패 — 런타임 예외. <b>실패가 아니라 "모름"</b>이다</li>
     * </ul>
     *
     * 두 번째를 실패로 단정해 보상을 돌리면, 실제로는 결제가 성공했는데 주문만 사라져
     * 돈이 빠진 채 살 물건이 없어진다. 그래서 즉시 결과를 조회하고, 확정되지 않을 때만
     * PENDING 을 유지한다.
     */
    public OrderPayResult pay(Long memberId, Long orderId, Boolean termsAgreed) {
        PayPreparation prepared = tx.prepare(memberId, orderId, termsAgreed);

        PaymentResult result;
        try {
            result = paymentPort.pay(
                    prepared.idempotencyKey(), orderId, memberId, prepared.amount());
        } catch (RuntimeException e) {
            //FeignException / RetryableException 등. 실패로 단정하지 않고 즉시 결과를 조회한다.
            //조회도 확정되지 않을 때만 PENDING 으로 두고 만료 배치가 다시 확인한다.
            log.warn("결제 응답을 받지 못했다 — 결과를 즉시 조회한다. orderId={}, reason={}",
                    orderId, e.toString());
            return resolveAfterUnknownResponse(memberId, orderId, prepared);
        }

        if (result.isSuccess()) {
            return applyPaymentSuccess(memberId, orderId, prepared.amount());
        }

        if (result.isFail()) {
            //잔액 부족 등 확정 실패. PENDING 으로 두어 충전 후 같은 주문에서 다시 결제한다.
            return paymentFailed(memberId, orderId, prepared, result.message());
        }

        //계약에 없는 미확정 상태를 실패로 닫지 않는다.
        return OrderPayResult.processing(orderId, prepared.amount());
    }

    private OrderPayResult resolveAfterUnknownResponse(
            Long memberId, Long orderId, PayPreparation prepared) {
        PaymentResult queried;
        try {
            queried = paymentResultQueryService.query(prepared.idempotencyKey());
        } catch (RuntimeException queryFailure) {
            log.warn("결제 결과 조회도 실패했다 — PENDING 유지. orderId={}, reason={}",
                    orderId, queryFailure.toString());
            return OrderPayResult.processing(orderId, prepared.amount());
        }

        if (queried.isSuccess()) {
            return applyPaymentSuccess(memberId, orderId, prepared.amount());
        }
        if (queried.isFail()) {
            return retrySamePaymentAttempt(memberId, orderId, prepared);
        }

        //NOT_FOUND는 조회 순간 아직 확정 기록이 없다는 뜻이다. 실패로 닫지 않는다.
        return OrderPayResult.processing(orderId, prepared.amount());
    }

    /** 조회에서 차감 실패가 확정된 경우에만 같은 키로 한 번 다시 실행한다. */
    private OrderPayResult retrySamePaymentAttempt(
            Long memberId, Long orderId, PayPreparation prepared) {
        PaymentResult retried;
        try {
            retried = paymentPort.pay(
                    prepared.idempotencyKey(), orderId, memberId, prepared.amount());
        } catch (RuntimeException retryFailure) {
            log.warn("같은 키 결제 재호출의 응답을 받지 못했다 — PENDING 유지. orderId={}, key={}, reason={}",
                    orderId, prepared.idempotencyKey(), retryFailure.toString());
            return OrderPayResult.processing(orderId, prepared.amount());
        }

        if (retried.isSuccess()) {
            return applyPaymentSuccess(memberId, orderId, prepared.amount());
        }
        if (retried.isFail()) {
            return paymentFailed(memberId, orderId, prepared, retried.message());
        }
        return OrderPayResult.processing(orderId, prepared.amount());
    }

    /**
     * 결제 성공 후 처리. <b>여기서 재고를 깎는다.</b>
     *
     * 차감이 실패하면 되돌릴 재고가 없으므로 <b>결제를 되돌린다</b>(환불).
     * 환불 호출은 DB 트랜잭션 밖에 둔다 — 네트워크 대기 중 잠금을 붙잡지 않기 위해서다.
     *
     * <b>환불까지 실패하면 주문을 닫지 않는다.</b> 돈이 빠졌는지 모르는 상태로 종료시키는
     * 것보다 PENDING 으로 남겨 재시도·알림 대상으로 두는 편이 안전하다.
     */
    public OrderPayResult applyPaymentSuccess(Long memberId, Long orderId, BigDecimal amount) {
        PaymentApplyResult applied;
        try {
            applied = tx.decreaseStockAndMarkPaid(orderId);
        } catch (BusinessException e) {
            if (e.getErrorCode() != ErrorCode.OUT_OF_STOCK) {
                throw e;
            }
            return refundAfterStockFailure(memberId, orderId, amount);
        }

        if (applied == PaymentApplyResult.ORDER_ALREADY_CLOSED) {
            return refundAfterOrderClosed(memberId, orderId, amount);
        }

        Order paid = tx.load(orderId);
        return new OrderPayResult(
                orderId,
                paid.getOrderState(),
                OrderPayResult.Outcome.PAID,
                amount,
                safeBalance(memberId),
                paid.getPaidAt(),
                null
        );
    }

    private OrderPayResult refundAfterStockFailure(Long memberId, Long orderId, BigDecimal amount) {
        //멱등키는 기존 규칙을 그대로 쓴다. 환불용 키를 새로 만들지 않는다.
        PaymentResult refund = paymentPort.refund(
                "order-" + orderId + "-refund", orderId, memberId, amount);

        if (!refund.isSuccess()) {
            log.error("재고 부족 환불 실패 — PENDING 유지, 재시도 대상. orderId={}", orderId);
            return OrderPayResult.processing(orderId, amount);
        }

        tx.markFailedWithoutRestore(orderId, OrderFailReason.OUT_OF_STOCK);
        return failed(orderId, amount, OrderPayResult.Outcome.OUT_OF_STOCK);
    }

    /**
     * payment 호출 중 사용자가 취소했거나 만료 배치가 먼저 주문을 닫은 경우.
     *
     * Order가 결제 진행 상태를 기록해 취소·만료를 금지하지 않기로 했으므로, 뒤늦게 확인된
     * 성공 결제는 환불로 되돌려야 한다. 주문 상태는 이미 종료됐으므로 다시 바꾸지 않는다.
     */
    private OrderPayResult refundAfterOrderClosed(Long memberId, Long orderId, BigDecimal amount) {
        PaymentResult refund = paymentPort.refund(
                "order-" + orderId + "-refund", orderId, memberId, amount);
        Order closed = tx.load(orderId);

        if (!refund.isSuccess()) {
            log.error("종료된 주문의 결제 환불 실패 — 수동 확인 대상. orderId={}, state={}",
                    orderId, closed.getOrderState());
            return new OrderPayResult(orderId, closed.getOrderState(), OrderPayResult.Outcome.PROCESSING,
                    amount, null, null, refund.message());
        }

        return OrderPayResult.paymentReversed(orderId, closed.getOrderState(), amount);
    }

    private OrderPayResult paymentFailed(
            Long memberId, Long orderId, PayPreparation prepared, String message) {
        boolean applied = tx.markPayFailed(orderId, prepared.attemptNo());
        Order order = tx.load(orderId);
        if (!applied) {
            if (order.getOrderState() == com.openbake.order.domain.OrderState.PAID) {
                return new OrderPayResult(
                        orderId,
                        order.getOrderState(),
                        OrderPayResult.Outcome.PAID,
                        prepared.amount(),
                        safeBalance(memberId),
                        order.getPaidAt(),
                        null
                );
            }
            return new OrderPayResult(
                    orderId,
                    order.getOrderState(),
                    OrderPayResult.Outcome.PROCESSING,
                    prepared.amount(),
                    null,
                    order.getPaidAt(),
                    "주문 상태가 변경되어 이전 결제 실패 응답을 반영하지 않았습니다."
            );
        }
        return OrderPayResult.paymentFailed(
                orderId, order.getOrderState(), prepared.amount(), safeBalance(memberId), message);
    }

    private OrderPayResult failed(Long orderId, BigDecimal amount, OrderPayResult.Outcome outcome) {
        return new OrderPayResult(
                orderId,
                tx.load(orderId).getOrderState(),
                outcome,
                amount,
                null,
                null,
                outcome == OrderPayResult.Outcome.OUT_OF_STOCK ? "재고가 소진되었습니다." : null
        );
    }

    /** 잔액 조회 실패가 이미 확정된 결제 결과 응답을 500으로 바꾸지 않게 한다. */
    private BigDecimal safeBalance(Long memberId) {
        try {
            return paymentPort.getBalance(memberId).balance();
        } catch (RuntimeException e) {
            log.warn("결제 결과 응답용 잔액 조회 실패 — 잔액 없이 응답한다. memberId={}, reason={}",
                    memberId, e.toString());
            return null;
        }
    }
}
