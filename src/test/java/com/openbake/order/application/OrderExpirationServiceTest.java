package com.openbake.order.application;

import com.openbake.order.application.port.PaymentPort;
import com.openbake.order.application.port.dto.PaymentResult;
import com.openbake.order.domain.Order;
import com.openbake.order.domain.OrderFailReason;
import com.openbake.order.domain.OrderItem;
import com.openbake.order.domain.OrderRepository;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderExpirationServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private PaymentPort paymentPort;
    @Mock private OrderPayService payService;
    @Mock private OrderPayTransactions payTransactions;
    @Mock private OrderExpirationTransactions expirationTransactions;

    private OrderExpirationService service;

    private static final Long ORDER_ID = 101L;
    private static final Long MEMBER_ID = 5L;
    private static final BigDecimal AMOUNT = new BigDecimal("5000");
    private static final int ATTEMPT_NO = 1;
    private static final String PAYMENT_KEY = "order-101-01";

    @BeforeEach
    void setUp() {
        service = new OrderExpirationService(
                orderRepository,
                paymentPort,
                new PaymentResultQueryService(paymentPort),
                payService,
                payTransactions,
                expirationTransactions
        );
    }

    @Test
    @DisplayName("결제 미시도 PENDING은 EXPIRED로 닫는다")
    void expiresNeverAttemptedOrder() {
        when(payTransactions.load(ORDER_ID)).thenReturn(pendingOrder(false));

        service.expire(ORDER_ID);

        verify(expirationTransactions).restoreAndExpire(ORDER_ID);
        verify(paymentPort, never()).getPayResult(anyString());
    }

    @Test
    @DisplayName("결제 시도 주문의 최신 결과가 SUCCESS면 PAID 복구 흐름을 탄다")
    void restoresPaidOrder() {
        when(payTransactions.load(ORDER_ID)).thenReturn(pendingOrder(true));
        when(paymentPort.getPayResult(PAYMENT_KEY)).thenReturn(new PaymentResult("SUCCESS", null));

        service.expire(ORDER_ID);

        verify(payService).applyPaymentSuccess(MEMBER_ID, ORDER_ID, AMOUNT);
        verify(expirationTransactions, never())
                .restoreAndFailIfCurrentAttempt(anyLong(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    @DisplayName("결제 시도 주문의 최신 결과가 FAIL이면 FAILED로 닫는다")
    void failsAttemptedOrder() {
        when(payTransactions.load(ORDER_ID)).thenReturn(pendingOrder(true));
        when(paymentPort.getPayResult(PAYMENT_KEY)).thenReturn(new PaymentResult("FAIL", "잔액 부족"));

        service.expire(ORDER_ID);

        verify(expirationTransactions).restoreAndFailIfCurrentAttempt(
                ORDER_ID, ATTEMPT_NO, OrderFailReason.PAYMENT_FAILED);
    }

    @Test
    @DisplayName("조회 API 자체가 실패하면 돈의 결과를 단정하지 않고 다음 배치로 넘긴다")
    void keepsPendingWhenQueryFails() {
        when(payTransactions.load(ORDER_ID)).thenReturn(pendingOrder(true));
        when(paymentPort.getPayResult(PAYMENT_KEY)).thenThrow(new RuntimeException("payment unavailable"));

        service.expire(ORDER_ID);

        verify(expirationTransactions, never()).restoreAndExpire(anyLong());
        verify(expirationTransactions, never())
                .restoreAndFailIfCurrentAttempt(anyLong(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    private Order pendingOrder(boolean attempted) {
        Order order = Order.createPending(MEMBER_ID, "김구매", SalesType.GENERAL,
                AMOUNT, LocalDateTime.now().minusMinutes(1));
        order.addItem(OrderItem.create(30L, null, null, 2, new BigDecimal("2500"),
                "소금빵", 10L, "빵집", LocalDate.now().plusDays(1), "img"));
        ReflectionTestUtils.setField(order, "orderId", ORDER_ID);
        if (attempted) {
            order.preparePayAttempt();
        }
        return order;
    }
}
