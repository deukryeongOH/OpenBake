package com.openbake.order.application;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.order.application.OrderPayTransactions.PayPreparation;
import com.openbake.order.application.OrderPayTransactions.PaymentApplyResult;
import com.openbake.order.application.port.PaymentPort;
import com.openbake.order.application.port.dto.BalanceInfo;
import com.openbake.order.application.port.dto.PaymentResult;
import com.openbake.order.domain.Order;
import com.openbake.order.domain.OrderFailReason;
import com.openbake.order.domain.OrderItem;
import com.openbake.order.domain.OrderState;
import com.openbake.order.domain.SalesType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 결제 흐름 테스트.
 *
 * 트랜잭션 조각({@link OrderPayTransactions})은 목으로 둔다. 여기서 확인하려는 것은
 * <b>외부 결제 결과에 따라 무엇을 되돌리는가</b>이지 DB 동작이 아니다.
 */
@ExtendWith(MockitoExtension.class)
class OrderPayServiceTest {

    @Mock
    private OrderPayTransactions tx;
    @Mock
    private PaymentPort paymentPort;

    private OrderPayService payService;

    private static final Long BUYER_ID = 5L;
    private static final Long ORDER_ID = 101L;
    private static final BigDecimal AMOUNT = new BigDecimal("5000");
    private static final int ATTEMPT_NO = 1;
    private static final String PAYMENT_KEY = "order-101-01";
    private static final String NEXT_PAYMENT_KEY = "order-101-02";

    @BeforeEach
    void setUp() {
        payService = new OrderPayService(tx, paymentPort, new PaymentResultQueryService(paymentPort));
    }

    @Test
    @DisplayName("결제 성공 → 재고 차감 후 PAID")
    void paid() {
        when(tx.prepare(BUYER_ID, ORDER_ID, true))
                .thenReturn(new PayPreparation(AMOUNT, ATTEMPT_NO, PAYMENT_KEY));
        when(paymentPort.pay(PAYMENT_KEY, ORDER_ID, BUYER_ID, AMOUNT))
                .thenReturn(new PaymentResult("SUCCESS", null));
        when(tx.decreaseStockAndMarkPaid(ORDER_ID)).thenReturn(PaymentApplyResult.PAID);
        when(tx.load(ORDER_ID)).thenReturn(paidOrder());
        when(paymentPort.getBalance(BUYER_ID)).thenReturn(new BalanceInfo(BUYER_ID, new BigDecimal("1000")));

        OrderPayResult result = payService.pay(BUYER_ID, ORDER_ID, true);

        assertThat(result.outcome()).isEqualTo(OrderPayResult.Outcome.PAID);
        verify(tx).decreaseStockAndMarkPaid(ORDER_ID);
        verify(paymentPort, never()).refund(any(), anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("잔액 부족은 200 + FAIL 로 오고, 충전 후 재결제할 수 있게 PENDING 을 유지한다")
    void paymentFailed() {
        when(tx.prepare(BUYER_ID, ORDER_ID, true))
                .thenReturn(new PayPreparation(AMOUNT, ATTEMPT_NO, PAYMENT_KEY));
        when(paymentPort.pay(PAYMENT_KEY, ORDER_ID, BUYER_ID, AMOUNT))
                .thenReturn(new PaymentResult("FAIL", "잔액이 부족합니다."));
        when(tx.markPayFailed(ORDER_ID, ATTEMPT_NO)).thenReturn(true);
        when(tx.load(ORDER_ID)).thenReturn(pendingOrder());

        OrderPayResult result = payService.pay(BUYER_ID, ORDER_ID, true);

        assertThat(result.outcome()).isEqualTo(OrderPayResult.Outcome.PAYMENT_FAILED);
        assertThat(result.orderState()).isEqualTo(OrderState.PENDING);
        verify(tx, never()).markFailed(anyLong(), any());
        //결제가 안 됐으므로 되돌릴 돈이 없다.
        verify(paymentPort, never()).refund(any(), anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("첫 결제가 FAIL이어도 Order가 다음 결제 요청을 차단하지 않는다")
    void retriesSameOrderAfterPaymentFail() {
        when(tx.prepare(BUYER_ID, ORDER_ID, true))
                .thenReturn(
                        new PayPreparation(AMOUNT, ATTEMPT_NO, PAYMENT_KEY),
                        new PayPreparation(AMOUNT, ATTEMPT_NO + 1, NEXT_PAYMENT_KEY));
        when(paymentPort.pay(any(), org.mockito.ArgumentMatchers.eq(ORDER_ID),
                org.mockito.ArgumentMatchers.eq(BUYER_ID), org.mockito.ArgumentMatchers.eq(AMOUNT)))
                .thenReturn(new PaymentResult("FAIL", "잔액이 부족합니다."));
        when(tx.markPayFailed(ORDER_ID, ATTEMPT_NO)).thenReturn(true);
        when(tx.markPayFailed(ORDER_ID, ATTEMPT_NO + 1)).thenReturn(true);
        when(tx.load(ORDER_ID)).thenReturn(pendingOrder());

        payService.pay(BUYER_ID, ORDER_ID, true);
        payService.pay(BUYER_ID, ORDER_ID, true);

        verify(paymentPort).pay(PAYMENT_KEY, ORDER_ID, BUYER_ID, AMOUNT);
        verify(paymentPort).pay(NEXT_PAYMENT_KEY, ORDER_ID, BUYER_ID, AMOUNT);
        verify(tx, never()).markFailed(anyLong(), any());
    }

    @Test
    @DisplayName("★ 결제는 됐는데 재고 차감이 실패하면 환불로 되돌린다")
    void refundsWhenStockRunsOut() {
        when(tx.prepare(BUYER_ID, ORDER_ID, true))
                .thenReturn(new PayPreparation(AMOUNT, ATTEMPT_NO, PAYMENT_KEY));
        when(paymentPort.pay(PAYMENT_KEY, ORDER_ID, BUYER_ID, AMOUNT))
                .thenReturn(new PaymentResult("SUCCESS", null));
        //주문서를 쓰는 동안 남이 먼저 사갔다.
        doThrow(new BusinessException(ErrorCode.OUT_OF_STOCK))
                .when(tx).decreaseStockAndMarkPaid(ORDER_ID);
        when(paymentPort.refund("order-101-refund", ORDER_ID, BUYER_ID, AMOUNT))
                .thenReturn(new PaymentResult("SUCCESS", null));
        when(tx.load(ORDER_ID)).thenReturn(failedOrder());

        OrderPayResult result = payService.pay(BUYER_ID, ORDER_ID, true);

        assertThat(result.outcome()).isEqualTo(OrderPayResult.Outcome.OUT_OF_STOCK);
        //차감한 재고를 되돌리는 게 아니라 결제를 되돌린다. 멱등키는 기존 규칙 그대로.
        verify(paymentPort).refund("order-101-refund", ORDER_ID, BUYER_ID, AMOUNT);
        verify(tx).markFailedWithoutRestore(ORDER_ID, OrderFailReason.OUT_OF_STOCK);
    }

    @Test
    @DisplayName("환불까지 실패하면 주문을 닫지 않고 PENDING 으로 남긴다")
    void keepsPendingWhenRefundFails() {
        when(tx.prepare(BUYER_ID, ORDER_ID, true))
                .thenReturn(new PayPreparation(AMOUNT, ATTEMPT_NO, PAYMENT_KEY));
        when(paymentPort.pay(PAYMENT_KEY, ORDER_ID, BUYER_ID, AMOUNT))
                .thenReturn(new PaymentResult("SUCCESS", null));
        doThrow(new BusinessException(ErrorCode.OUT_OF_STOCK))
                .when(tx).decreaseStockAndMarkPaid(ORDER_ID);
        when(paymentPort.refund("order-101-refund", ORDER_ID, BUYER_ID, AMOUNT))
                .thenReturn(new PaymentResult("FAIL", null));

        OrderPayResult result = payService.pay(BUYER_ID, ORDER_ID, true);

        //돈이 빠졌는지 모르는 상태로 종료시키지 않는다. 재시도·알림 대상으로 남긴다.
        assertThat(result.outcome()).isEqualTo(OrderPayResult.Outcome.PROCESSING);
        verify(tx, never()).markFailedWithoutRestore(anyLong(), any());
    }

    @Test
    @DisplayName("★ 타임아웃 뒤 조회도 미확정이면 아무것도 되돌리지 않고 PENDING 을 유지한다")
    void timeoutIsNotFailure() {
        when(tx.prepare(BUYER_ID, ORDER_ID, true))
                .thenReturn(new PayPreparation(AMOUNT, ATTEMPT_NO, PAYMENT_KEY));
        when(paymentPort.pay(PAYMENT_KEY, ORDER_ID, BUYER_ID, AMOUNT))
                .thenThrow(new RuntimeException("read timeout"));
        when(paymentPort.getPayResult(PAYMENT_KEY)).thenReturn(new PaymentResult("NOT_FOUND", null));

        OrderPayResult result = payService.pay(BUYER_ID, ORDER_ID, true);

        assertThat(result.outcome()).isEqualTo(OrderPayResult.Outcome.PROCESSING);
        assertThat(result.orderState()).isEqualTo(OrderState.PENDING);
        //실패로 단정해 보상을 돌리면, 실제로 결제가 성공한 경우 돈만 빠지고 주문이 사라진다.
        verify(tx, never()).markFailed(anyLong(), any());
        verify(paymentPort, never()).refund(any(), anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("타임아웃이어도 즉시 조회가 SUCCESS 면 주문을 PAID 로 복원한다")
    void timeoutThenSuccess() {
        when(tx.prepare(BUYER_ID, ORDER_ID, true))
                .thenReturn(new PayPreparation(AMOUNT, ATTEMPT_NO, PAYMENT_KEY));
        when(paymentPort.pay(PAYMENT_KEY, ORDER_ID, BUYER_ID, AMOUNT))
                .thenThrow(new RuntimeException("read timeout"));
        when(paymentPort.getPayResult(PAYMENT_KEY)).thenReturn(new PaymentResult("SUCCESS", null));
        when(tx.decreaseStockAndMarkPaid(ORDER_ID)).thenReturn(PaymentApplyResult.PAID);
        when(tx.load(ORDER_ID)).thenReturn(paidOrder());
        when(paymentPort.getBalance(BUYER_ID)).thenReturn(new BalanceInfo(BUYER_ID, new BigDecimal("1000")));

        OrderPayResult result = payService.pay(BUYER_ID, ORDER_ID, true);

        assertThat(result.outcome()).isEqualTo(OrderPayResult.Outcome.PAID);
        verify(tx).decreaseStockAndMarkPaid(ORDER_ID);
    }

    @Test
    @DisplayName("타임아웃 뒤 조회가 FAIL이면 같은 먱등키로 한 번 재결제한다")
    void timeoutThenFail() {
        when(tx.prepare(BUYER_ID, ORDER_ID, true))
                .thenReturn(new PayPreparation(AMOUNT, ATTEMPT_NO, PAYMENT_KEY));
        when(paymentPort.pay(PAYMENT_KEY, ORDER_ID, BUYER_ID, AMOUNT))
                .thenThrow(new RuntimeException("read timeout"))
                .thenReturn(new PaymentResult("FAIL", "잔액 부족"));
        when(paymentPort.getPayResult(PAYMENT_KEY)).thenReturn(new PaymentResult("FAIL", "잔액 부족"));
        when(tx.markPayFailed(ORDER_ID, ATTEMPT_NO)).thenReturn(true);
        when(tx.load(ORDER_ID)).thenReturn(pendingOrder());

        OrderPayResult result = payService.pay(BUYER_ID, ORDER_ID, true);

        assertThat(result.outcome()).isEqualTo(OrderPayResult.Outcome.PAYMENT_FAILED);
        assertThat(result.orderState()).isEqualTo(OrderState.PENDING);
        verify(paymentPort, times(2)).pay(PAYMENT_KEY, ORDER_ID, BUYER_ID, AMOUNT);
    }

    @Test
    @DisplayName("결제 호출 중 주문이 먼저 만료됐으면 뒤늦은 성공 결제를 환불한다")
    void refundsPaymentCompletedAfterExpiration() {
        Order expired = pendingOrder();
        expired.markExpired();
        when(tx.prepare(BUYER_ID, ORDER_ID, true))
                .thenReturn(new PayPreparation(AMOUNT, ATTEMPT_NO, PAYMENT_KEY));
        when(paymentPort.pay(PAYMENT_KEY, ORDER_ID, BUYER_ID, AMOUNT))
                .thenReturn(new PaymentResult("SUCCESS", null));
        when(tx.decreaseStockAndMarkPaid(ORDER_ID)).thenReturn(PaymentApplyResult.ORDER_ALREADY_CLOSED);
        when(paymentPort.refund("order-101-refund", ORDER_ID, BUYER_ID, AMOUNT))
                .thenReturn(new PaymentResult("SUCCESS", null));
        when(tx.load(ORDER_ID)).thenReturn(expired);

        OrderPayResult result = payService.pay(BUYER_ID, ORDER_ID, true);

        assertThat(result.orderState()).isEqualTo(OrderState.EXPIRED);
        assertThat(result.outcome()).isEqualTo(OrderPayResult.Outcome.PAYMENT_REVERSED);
        verify(paymentPort).refund("order-101-refund", ORDER_ID, BUYER_ID, AMOUNT);
    }

    private Order paidOrder() {
        Order order = pendingOrder();
        order.markPaid();
        return order;
    }

    private Order failedOrder() {
        Order order = pendingOrder();
        order.markFailed(OrderFailReason.PAYMENT_FAILED);
        return order;
    }

    private Order pendingOrder() {
        Order order = Order.createPending(BUYER_ID, "김구매", SalesType.GENERAL,
                AMOUNT, LocalDateTime.now().plusMinutes(15));
        order.addItem(OrderItem.create(30L, null, null, 2, new BigDecimal("2500"),
                "시그니처 소금빵", 10L, "이세종 베이커리", LocalDate.now().plusDays(1), "img"));
        ReflectionTestUtils.setField(order, "orderId", ORDER_ID);
        return order;
    }
}
