package com.openbake.order.application;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.order.application.port.CartPort;
import com.openbake.order.application.port.ProductPort;
import com.openbake.order.application.port.ReservationPort;
import com.openbake.order.application.port.dto.ProductInfo;
import com.openbake.order.domain.Order;
import com.openbake.order.domain.OrderItem;
import com.openbake.order.domain.OrderRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 결제 전후 DB 조각 테스트. <b>가격 재검증</b>이 여기 있다.
 */
@ExtendWith(MockitoExtension.class)
class OrderPayTransactionsTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductPort productPort;
    @Mock
    private CartPort cartPort;
    @Mock
    private OrderStockRestorer stockRestorer;
    @Mock
    private ReservationPort reservationPort;

    private OrderPayTransactions tx;

    private static final Long BUYER_ID = 5L;
    private static final Long ORDER_ID = 101L;
    private static final Long PRODUCT_ID = 30L;
    private static final Long DROP_ID = 7L;
    private static final LocalDate PICK_UP_DATE = LocalDate.now().plusDays(1);

    @BeforeEach
    void setUp() {
        tx = new OrderPayTransactions(orderRepository, productPort, cartPort, stockRestorer, reservationPort);
    }

    @Test
    @DisplayName("약관에 동의하지 않으면 결제로 넘어가지 않는다")
    void termsNotAgreed() {
        assertThatThrownBy(() -> tx.prepare(BUYER_ID, ORDER_ID, false))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TERMS_NOT_AGREED);
    }

    @Test
    @DisplayName("가격이 그대로면 통과하고 payAttemptedAt 을 남긴다")
    void priceUnchanged() {
        Order order = pendingOrder("2500");
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(product(2500)));

        OrderPayTransactions.PayPreparation prepared = tx.prepare(BUYER_ID, ORDER_ID, true);

        assertThat(prepared.amount()).isEqualByComparingTo("5000");
        //이 값이 없으면 타임아웃 시 "결제를 시도했다"는 사실이 사라진다.
        assertThat(order.getPayAttemptedAt()).isNotNull();
    }

    @Test
    @DisplayName("★ 주문서 표시가와 현재 가격이 다르면 결제하지 않고 막는다")
    void priceChanged() {
        Order order = pendingOrder("2500");
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        //주문서를 보는 동안 판매자가 3,000원으로 올렸다.
        when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(product(3000)));

        assertThatThrownBy(() -> tx.prepare(BUYER_ID, ORDER_ID, true))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PRICE_CHANGED);

        //자동 재계산하지 않는다. 스냅샷도 그대로 둔다 —
        //덮으면 "사용자가 무슨 금액에 동의했는가"의 기록이 사라진다.
        assertThat(order.getItems().getFirst().getUnitPriceSnapshot()).isEqualByComparingTo("2500");
        assertThat(order.getPayAttemptedAt()).isNull();
    }

    @Test
    @DisplayName("드롭 주문은 가격을 재검증하지 않는다 — 바뀔 경로가 없다")
    void dropSkipsPriceCheck() {
        Order order = dropOrder();
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        tx.prepare(BUYER_ID, ORDER_ID, true);

        verify(productPort, never()).findProduct(any());
    }

    @Test
    @DisplayName("만료된 주문서로는 결제할 수 없다")
    void expiredOrderSheet() {
        Order order = pendingOrder("2500");
        ReflectionTestUtils.setField(order, "reservationExpiresAt", LocalDateTime.now().minusMinutes(1));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> tx.prepare(BUYER_ID, ORDER_ID, true))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_ORDER_STATE);
    }

    @Test
    @DisplayName("재고 차감이 성공하면 PAID 가 되고 주문한 장바구니 항목만 지운다")
    void decreaseStockAndMarkPaid() {
        Order order = pendingOrderFromCart();
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(productPort.decreaseStock(PRODUCT_ID, 2)).thenReturn(true);

        OrderPayTransactions.PaymentApplyResult result = tx.decreaseStockAndMarkPaid(ORDER_ID);

        assertThat(result).isEqualTo(OrderPayTransactions.PaymentApplyResult.PAID);
        assertThat(order.getOrderState()).isEqualTo(OrderState.PAID);
        //슬롯은 종료 전이에서 자동 반납된다. 서비스가 따로 지우지 않는다.
        assertThat(order.getActiveMemberId()).isNull();
        verify(cartPort).removeItems(BUYER_ID, List.of(12L));
    }

    @Test
    @DisplayName("재고가 모자라면 OUT_OF_STOCK 을 던져 트랜잭션째 되돌린다")
    void decreaseStockFails() {
        Order order = pendingOrder("2500");
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(productPort.decreaseStock(PRODUCT_ID, 2)).thenReturn(false);

        assertThatThrownBy(() -> tx.decreaseStockAndMarkPaid(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.OUT_OF_STOCK);

        assertThat(order.getOrderState()).isEqualTo(OrderState.PENDING);
        verify(cartPort, never()).removeItems(any(), anyList());
    }

    @Test
    @DisplayName("드롭 주문은 결제 성공 시 재고를 다시 깎지 않는다 — lock-start 에서 이미 깎였다")
    void dropDoesNotDecreaseAgain() {
        Order order = dropOrder();
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        OrderPayTransactions.PaymentApplyResult result = tx.decreaseStockAndMarkPaid(ORDER_ID);

        assertThat(result).isEqualTo(OrderPayTransactions.PaymentApplyResult.PAID);
        assertThat(order.getOrderState()).isEqualTo(OrderState.PAID);
        verify(productPort, never()).decreaseStock(any(), anyInt());
    }

    /**
     * docs/10 3.1절. 재고를 다시 깎지는 않지만, drop_entry 선점은 확정해야 한다 —
     * 안 하면 2단계(선점 TTL 스위퍼)가 결제 완료 건까지 방치된 선점으로 오판해 회수한다.
     */
    @Test
    @DisplayName("드롭 주문은 결제 성공 시 선점을 확정한다 — RESERVED를 COMPLETED로 옮긴다")
    void dropCompletesReservationOnPayment() {
        Order order = dropOrder();
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        tx.decreaseStockAndMarkPaid(ORDER_ID);

        verify(reservationPort).complete(DROP_ID, BUYER_ID);
    }

    @Test
    @DisplayName("일반 상품 주문은 선점 확정을 호출하지 않는다 — 드롭 전용 계약이다")
    void generalOrderDoesNotCompleteReservation() {
        Order order = pendingOrderFromCart();
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(productPort.decreaseStock(PRODUCT_ID, 2)).thenReturn(true);

        tx.decreaseStockAndMarkPaid(ORDER_ID);

        verify(reservationPort, never()).complete(any(), any());
    }

    @Test
    @DisplayName("결제 응답 전에 주문이 만료됐으면 재고를 건드리지 않고 종료 상태를 알린다")
    void paymentSuccessAfterExpiration() {
        Order order = pendingOrder("2500");
        order.markExpired();
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        OrderPayTransactions.PaymentApplyResult result = tx.decreaseStockAndMarkPaid(ORDER_ID);

        assertThat(result).isEqualTo(OrderPayTransactions.PaymentApplyResult.ORDER_ALREADY_CLOSED);
        verify(productPort, never()).decreaseStock(any(), anyInt());
        verify(cartPort, never()).removeItems(any(), anyList());
    }

    private ProductInfo product(int price) {
        return new ProductInfo(PRODUCT_ID, 10L, "시그니처 소금빵", price, "img",
                true, false, Set.of(PICK_UP_DATE), 10);
    }

    private Order pendingOrder(String unitPrice) {
        return order(unitPrice, SalesType.GENERAL, null, null);
    }

    private Order pendingOrderFromCart() {
        return order("2500", SalesType.GENERAL, null, 12L);
    }

    private Order dropOrder() {
        return order("2500", SalesType.DROP, DROP_ID, null);
    }

    private Order order(String unitPrice, SalesType salesType, Long dropId, Long cartItemId) {
        Order order = Order.createPending(BUYER_ID, "김구매", salesType,
                new BigDecimal(unitPrice).multiply(BigDecimal.valueOf(2)),
                LocalDateTime.now().plusMinutes(15));
        order.addItem(OrderItem.create(PRODUCT_ID, dropId, cartItemId, 2, new BigDecimal(unitPrice),
                "시그니처 소금빵", 10L, "이세종 베이커리", PICK_UP_DATE, "img"));
        ReflectionTestUtils.setField(order, "orderId", ORDER_ID);
        return order;
    }
}
