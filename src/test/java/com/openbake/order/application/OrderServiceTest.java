package com.openbake.order.application;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.common.response.ApiResponse;
import com.openbake.order.application.port.CartPort;
import com.openbake.order.application.port.DropPort;
import com.openbake.order.application.port.MemberPort;
import com.openbake.order.application.port.PaymentPort;
import com.openbake.order.application.port.ProductPort;
import com.openbake.order.application.port.ReservationPort;
import com.openbake.order.application.port.SellerPort;
import com.openbake.order.application.port.dto.BalanceInfo;
import com.openbake.order.application.port.dto.CartItemInfo;
import com.openbake.order.application.port.dto.DropInfo;
import com.openbake.order.application.port.dto.DropReservationInfo;
import com.openbake.order.application.port.dto.MemberInfo;
import com.openbake.order.application.port.dto.PaymentResult;
import com.openbake.order.application.port.dto.ProductInfo;
import com.openbake.order.application.port.dto.SellerInfo;
import com.openbake.order.domain.Order;
import com.openbake.order.domain.OrderItem;
import com.openbake.order.domain.OrderRepository;
import com.openbake.order.domain.OrderState;
import com.openbake.order.domain.SalesType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CartPort cartPort;
    @Mock
    private ProductPort productPort;
    @Mock
    private DropPort dropPort;
    @Mock
    private ReservationPort reservationPort;
    @Mock
    private PaymentPort paymentPort;
    @Mock
    private SellerPort sellerPort;
    @Mock
    private MemberPort memberPort;

    private OrderService orderService;

    private static final Long BUYER_ID = 5L;
    private static final Long SELLER_ID = 10L;
    private static final Long PRODUCT_ID = 30L;
    private static final Long DROP_ID = 7L;
    private static final LocalDate PICK_UP_DATE = LocalDate.now().plusDays(1);

    @BeforeEach
    void setUp() {
        //조립기와 복구기는 얇은 부품이라 목이 아니라 실제 인스턴스를 쓴다.
        //검증 규칙 자체가 이 테스트의 대상이기 때문이다.
        OrderSnapshotAssembler assembler = new OrderSnapshotAssembler(sellerPort, memberPort);
        OrderStockRestorer restorer = new OrderStockRestorer(productPort, reservationPort);

        orderService = new OrderService(
                orderRepository, cartPort, productPort, dropPort, reservationPort,
                paymentPort, sellerPort, memberPort, assembler, restorer);

        ReflectionTestUtils.setField(orderService, "reservationTtl", Duration.ofMinutes(15));
    }

    @Nested
    @DisplayName("주문 생성")
    class Create {

        @Test
        @DisplayName("장바구니 경로 — 수량·픽업일을 요청이 아니라 장바구니에서 읽는다")
        void fromCart() {
            givenBuyer();
            givenSeller();
            givenNoActiveOrder();
            givenSaveEchoes();

            //요청에는 cartItemIds 만 있다. 수량 3과 픽업일은 장바구니에서 온다.
            when(cartPort.findItemsForOrder(BUYER_ID, List.of(12L)))
                    .thenReturn(List.of(new CartItemInfo(12L, PRODUCT_ID, 3, PICK_UP_DATE)));
            when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(generalProduct(2500, 10)));

            OrderCreateResult result = orderService.create(BUYER_ID,
                    new OrderCreateCommand(List.of(12L), null, null, null, null));

            Order saved = captureSavedOrder();
            assertThat(saved.getSalesType()).isEqualTo(SalesType.GENERAL);
            assertThat(saved.getOrderState()).isEqualTo(OrderState.PENDING);
            assertThat(saved.getTotalAmount()).isEqualByComparingTo("7500");

            OrderItem item = saved.getItems().getFirst();
            assertThat(item.getQuantity()).isEqualTo(3);
            assertThat(item.getPickUpDate()).isEqualTo(PICK_UP_DATE);
            //결제 성공 후 이 항목만 장바구니에서 지우기 위한 근거다.
            assertThat(item.getSourceCartItemId()).isEqualTo(12L);
            assertThat(item.getDropId()).isNull();

            assertThat(result.orderState()).isEqualTo(OrderState.PENDING);
        }

        @Test
        @DisplayName("주문 생성은 재고를 깎지 않는다 — 일반 상품 차감은 결제 성공 뒤다")
        void doesNotDecreaseStock() {
            givenBuyer();
            givenSeller();
            givenNoActiveOrder();
            givenSaveEchoes();
            when(cartPort.findItemsForOrder(BUYER_ID, List.of(12L)))
                    .thenReturn(List.of(new CartItemInfo(12L, PRODUCT_ID, 1, PICK_UP_DATE)));
            when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(generalProduct(2500, 10)));

            orderService.create(BUYER_ID, new OrderCreateCommand(List.of(12L), null, null, null, null));

            verify(productPort, never()).decreaseStock(any(), org.mockito.ArgumentMatchers.anyInt());
        }

        @Test
        @DisplayName("바로 주문 경로 — 드롭 상품을 넣으면 막힌다(선점 우회 방지)")
        void directOrderRejectsDropProduct() {
            givenBuyer();
            givenNoActiveOrder();
            //generalType=false 인 상품. lock-start 를 건너뛰고 사려는 시도다.
            when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(
                    new ProductInfo(PRODUCT_ID, SELLER_ID, "드롭 소금빵", 2500, "img",
                            false, false, Set.of(PICK_UP_DATE), 10)));

            assertThatThrownBy(() -> orderService.create(BUYER_ID,
                    new OrderCreateCommand(null, PRODUCT_ID, 1, null, PICK_UP_DATE)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_PRODUCT_TYPE);

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("드롭 경로 — 수량은 요청이 아니라 선점값을 읽는다")
        void fromDropReadsReservedQuantity() {
            givenBuyer();
            givenSeller();
            givenNoActiveOrder();
            givenSaveEchoes();

            when(reservationPort.getReservation(DROP_ID, BUYER_ID))
                    .thenReturn(new DropReservationInfo(4, true));
            when(dropPort.getDrop(DROP_ID)).thenReturn(new DropInfo(
                    DROP_ID, PRODUCT_ID, SELLER_ID, "시그니처 소금빵", 3000, "img", Set.of(PICK_UP_DATE)));

            orderService.create(BUYER_ID, new OrderCreateCommand(null, null, null, DROP_ID, PICK_UP_DATE));

            Order saved = captureSavedOrder();
            assertThat(saved.getSalesType()).isEqualTo(SalesType.DROP);
            //클라이언트는 수량을 보내지 않았다. 선점 수량 4가 그대로 들어간다.
            assertThat(saved.getItems().getFirst().getQuantity()).isEqualTo(4);
            assertThat(saved.getTotalAmount()).isEqualByComparingTo("12000");
            //드롭 주문도 정산을 위해 productId 를 채운다.
            assertThat(saved.getItems().getFirst().getProductId()).isEqualTo(PRODUCT_ID);
        }

        @Test
        @DisplayName("선점하지 않은 드롭은 주문할 수 없다")
        void dropWithoutReservation() {
            givenBuyer();
            givenNoActiveOrder();
            when(reservationPort.getReservation(DROP_ID, BUYER_ID))
                    .thenReturn(new DropReservationInfo(0, false));

            assertThatThrownBy(() -> orderService.create(BUYER_ID,
                    new OrderCreateCommand(null, null, null, DROP_ID, PICK_UP_DATE)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.STOCK_NOT_RESERVED);
        }

        @Test
        @DisplayName("진행 중 주문이 있으면 일반 주문은 막힌다")
        void blockedByActiveOrder() {
            when(orderRepository.findByActiveMemberIdForUpdate(BUYER_ID))
                    .thenReturn(Optional.of(pendingGeneralOrder(99L)));

            assertThatThrownBy(() -> orderService.create(BUYER_ID,
                    new OrderCreateCommand(List.of(12L), null, null, null, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_REQUEST);

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("드롭 주문은 우선권이 있어 기존 진행 중 주문을 만료시키고 통과한다")
        void dropTakesPriority() {
            Order existing = pendingGeneralOrder(99L);
            when(orderRepository.findByActiveMemberIdForUpdate(BUYER_ID)).thenReturn(Optional.of(existing));
            givenBuyer();
            givenSeller();
            givenSaveEchoes();
            when(reservationPort.getReservation(DROP_ID, BUYER_ID))
                    .thenReturn(new DropReservationInfo(1, true));
            when(dropPort.getDrop(DROP_ID)).thenReturn(new DropInfo(
                    DROP_ID, PRODUCT_ID, SELLER_ID, "시그니처 소금빵", 3000, "img", Set.of(PICK_UP_DATE)));

            OrderCreateResult result = orderService.create(BUYER_ID,
                    new OrderCreateCommand(null, null, null, DROP_ID, PICK_UP_DATE));

            //대기열을 통과해 선점한 재고는 다시 만들 수 없다. 되돌릴 수 있는 쪽이 양보한다.
            assertThat(existing.getOrderState()).isEqualTo(OrderState.EXPIRED);
            assertThat(existing.getActiveMemberId()).isNull();
            assertThat(result.yieldedOrderId()).isEqualTo(99L);
        }

        @Test
        @DisplayName("경로를 둘 이상 보내면 거부한다")
        void ambiguousRoute() {
            assertThatThrownBy(() -> orderService.create(BUYER_ID,
                    new OrderCreateCommand(List.of(12L), PRODUCT_ID, 1, null, PICK_UP_DATE)))
                    .isInstanceOf(BusinessException.class);

            verifyNoInteractions(orderRepository);
        }

        @Test
        @DisplayName("재고가 모자라면 주문서를 만들지 않는다 — 결제 후 품절을 대부분 걸러낸다")
        void insufficientStock() {
            givenBuyer();
            givenNoActiveOrder();
            when(productPort.findProduct(PRODUCT_ID)).thenReturn(Optional.of(generalProduct(2500, 1)));

            assertThatThrownBy(() -> orderService.create(BUYER_ID,
                    new OrderCreateCommand(null, PRODUCT_ID, 5, null, PICK_UP_DATE)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.OUT_OF_STOCK);
        }
    }

    @Nested
    @DisplayName("취소")
    class Cancel {

        @Test
        @DisplayName("결제 전 취소는 EXPIRED 이고 환불하지 않는다")
        void beforePayment() {
            Order order = pendingGeneralOrder(101L);
            when(orderRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(order));

            OrderCancelResult result = orderService.cancel(BUYER_ID, 101L);

            assertThat(result.orderState()).isEqualTo(OrderState.EXPIRED);
            assertThat(result.refundAmount()).isEqualByComparingTo("0");
            //일반 상품은 PENDING 동안 재고를 잡지 않았으므로 되돌릴 것이 없다.
            verifyNoInteractions(paymentPort);
            verify(productPort, never()).rollbackStock(any(), org.mockito.ArgumentMatchers.anyInt());
        }

        @Test
        @DisplayName("결제 시도 이력이 있어도 Order가 취소를 차단하지 않는다")
        void attemptedPaymentDoesNotBlockCancel() {
            Order order = pendingGeneralOrder(101L);
            order.preparePayAttempt();
            when(orderRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(order));

            OrderCancelResult result = orderService.cancel(BUYER_ID, 101L);

            assertThat(result.orderState()).isEqualTo(OrderState.EXPIRED);
            verifyNoInteractions(paymentPort);
        }

        @Test
        @DisplayName("결제 후 취소는 환불하고 재고를 되돌린다")
        void afterPayment() {
            Order order = pendingGeneralOrder(101L);
            order.markPaid();
            when(orderRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(order));
            when(paymentPort.refund("order-101-refund", 101L, BUYER_ID, new BigDecimal("5000")))
                    .thenReturn(new PaymentResult("SUCCESS", null));
            when(paymentPort.getBalance(BUYER_ID))
                    .thenReturn(new BalanceInfo(BUYER_ID, new BigDecimal("10000")));

            OrderCancelResult result = orderService.cancel(BUYER_ID, 101L);

            assertThat(result.orderState()).isEqualTo(OrderState.CANCELED);
            verify(productPort).rollbackStock(PRODUCT_ID, 2);
        }

        @Test
        @DisplayName("항목이 하나라도 확정됐으면 취소할 수 없다 — 정산이 이미 나갔다")
        void blockedByConfirmedItem() {
            Order order = pendingGeneralOrder(101L);
            order.markPaid();
            order.confirmItem(order.getItems().getFirst());
            when(orderRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancel(BUYER_ID, 101L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ORDER_NOT_CANCELABLE);
        }

        @Test
        @DisplayName("타인의 주문은 취소할 수 없다")
        void notOwned() {
            when(orderRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(pendingGeneralOrder(101L)));

            assertThatThrownBy(() -> orderService.cancel(999L, 101L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
        }

    }

    @Nested
    @DisplayName("판매자 조회")
    class SellerView {

        @Test
        @DisplayName("판매자 화면에는 자기 항목만 담기고 금액도 자기 몫 소계다")
        void ownItemsOnly() {
            //A빵집 5,000 + B빵집 3,000 = 합계 8,000 인 주문
            Order order = Order.createPending(BUYER_ID, "김구매", SalesType.GENERAL,
                    new BigDecimal("8000"), LocalDateTime.now().plusMinutes(15));
            order.addItem(item(PRODUCT_ID, SELLER_ID, 2, "2500", "A빵"));
            order.addItem(item(31L, 11L, 1, "3000", "B빵"));
            ReflectionTestUtils.setField(order, "orderId", 101L);
            order.markPaid();

            when(sellerPort.getCurrentSellerId()).thenReturn(Optional.of(SELLER_ID));
            when(orderRepository.findBySellerId(org.mockito.ArgumentMatchers.eq(SELLER_ID), anyList(), any()))
                    .thenReturn(new PageImpl<>(List.of(order)));

            SellerOrderPageResult result = orderService.getSellerOrders(null, 0, 10);

            SellerOrderSummaryResult summary = result.content().getFirst();
            assertThat(summary.items()).hasSize(1);
            assertThat(summary.items().getFirst().productName()).isEqualTo("A빵");
            //주문 전체 8,000 이 아니라 자기 몫 5,000 이어야 한다.
            assertThat(summary.sellerAmount()).isEqualByComparingTo("5000");
        }

        @Test
        @DisplayName("승인된 판매자가 아니면 접근이 거부된다")
        void rejectNonSeller() {
            when(sellerPort.getCurrentSellerId()).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getSellerOrders(null, 0, 10))
                    .isInstanceOf(BusinessException.class);

            verifyNoInteractions(orderRepository);
        }
    }

    @Nested
    @DisplayName("주문 상세")
    class Detail {

        @Test
        @DisplayName("연락처는 sellerId 가 아니라 판매자의 memberId 로 조회한다")
        void resolvesPhoneByMemberId() {
            Order order = pendingGeneralOrder(101L);
            when(orderRepository.findById(101L)).thenReturn(Optional.of(order));
            when(sellerPort.findSeller(SELLER_ID)).thenReturn(Optional.of(
                    new SellerInfo(SELLER_ID, 20L, "이세종 베이커리", "서울시 강남구 테헤란로 1")));
            when(memberPort.getMember(20L))
                    .thenReturn(ApiResponse.ok(new MemberInfo("이세종", "010-1234-5678")));

            OrderDetailResult result = orderService.getOrderDetail(BUYER_ID, 101L);

            OrderDetailResult.SellerInfo seller = result.items().getFirst().seller();
            assertThat(seller.address()).isEqualTo("서울시 강남구 테헤란로 1");
            assertThat(seller.phoneNumber()).isEqualTo("010-1234-5678");
            //상호명은 조회 시점 값이 아니라 주문 시점 스냅샷이다.
            assertThat(seller.sellerName()).isEqualTo("이세종 베이커리(주문시점)");

            //sellerId 로 회원을 조회하면 엉뚱한 사람의 번호가 나간다.
            verify(memberPort, never()).getMember(SELLER_ID);
        }

        @Test
        @DisplayName("판매자 조회에 실패해도 상호명 스냅샷은 남는다")
        void sellerMissing() {
            when(orderRepository.findById(101L)).thenReturn(Optional.of(pendingGeneralOrder(101L)));
            when(sellerPort.findSeller(SELLER_ID)).thenReturn(Optional.empty());

            OrderDetailResult result = orderService.getOrderDetail(BUYER_ID, 101L);

            OrderDetailResult.SellerInfo seller = result.items().getFirst().seller();
            assertThat(seller.sellerName()).isEqualTo("이세종 베이커리(주문시점)");
            assertThat(seller.address()).isNull();
            verifyNoInteractions(memberPort);
        }
    }

    // ── 픽스처 ──────────────────────────────────────────────────

    private void givenBuyer() {
        when(memberPort.getMember(BUYER_ID))
                .thenReturn(ApiResponse.ok(new MemberInfo("김구매", "010-0000-0000")));
    }

    private void givenSeller() {
        when(sellerPort.findSeller(SELLER_ID)).thenReturn(Optional.of(
                new SellerInfo(SELLER_ID, 20L, "이세종 베이커리", "서울시 강남구")));
    }

    private void givenNoActiveOrder() {
        when(orderRepository.findByActiveMemberIdForUpdate(BUYER_ID)).thenReturn(Optional.empty());
    }

    private void givenSaveEchoes() {
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Order captureSavedOrder() {
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        return captor.getValue();
    }

    private ProductInfo generalProduct(int price, int remainQuantity) {
        return new ProductInfo(PRODUCT_ID, SELLER_ID, "시그니처 소금빵", price, "img",
                true, false, Set.of(PICK_UP_DATE), remainQuantity);
    }

    private Order pendingGeneralOrder(Long orderId) {
        Order order = Order.createPending(BUYER_ID, "김구매", SalesType.GENERAL,
                new BigDecimal("5000"), LocalDateTime.now().plusMinutes(15));
        order.addItem(item(PRODUCT_ID, SELLER_ID, 2, "2500", "시그니처 소금빵"));
        ReflectionTestUtils.setField(order, "orderId", orderId);
        return order;
    }

    private OrderItem item(Long productId, Long sellerId, int quantity, String unitPrice, String name) {
        OrderItem item = OrderItem.create(productId, null, null, quantity,
                new BigDecimal(unitPrice), name, sellerId, "이세종 베이커리(주문시점)", PICK_UP_DATE, "img");
        ReflectionTestUtils.setField(item, "orderItemId", productId * 10);
        return item;
    }
}
