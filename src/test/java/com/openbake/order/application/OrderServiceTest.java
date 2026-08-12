package com.openbake.order.application;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.response.ApiResponse;
import com.openbake.order.application.port.CartPort;
import com.openbake.order.application.port.DropPort;
import com.openbake.order.application.port.MemberPort;
import com.openbake.order.application.port.PaymentPort;
import com.openbake.order.application.port.ReservationPort;
import com.openbake.order.application.port.SellerPort;
import com.openbake.order.application.port.SettlementPort;
import com.openbake.order.application.port.dto.BalanceInfo;
import com.openbake.order.application.port.dto.CartInfo;
import com.openbake.order.application.port.dto.DropInfo;
import com.openbake.order.application.port.dto.MemberInfo;
import com.openbake.order.application.port.dto.PaymentResult;
import com.openbake.order.application.port.dto.SellerInfo;
import com.openbake.order.domain.Order;
import com.openbake.order.domain.OrderItem;
import com.openbake.order.domain.OrderRepository;
import com.openbake.order.domain.OrderState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    private PaymentPort paymentPort;
    @Mock
    private SellerPort sellerPort;
    @Mock
    private MemberPort memberPort;
    @Mock
    private ReservationPort reservationPort;
    @Mock
    private DropPort dropPort;
    @Mock
    private OrderReservationReleaser reservationReleaser;
    @Mock
    private SettlementPort settlementPort;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderRepository,
                cartPort,
                paymentPort,
                sellerPort,
                memberPort,
                reservationPort,
                dropPort,
                reservationReleaser,
                settlementPort
        );
    }

    @Test
    @DisplayName("판매자 본인 판매내역을 조회한다")
    void getSellerOrders() {
        // given
        Order order = createOrder(101L, 5L, 10L, 7L);

        when(sellerPort.getCurrentSellerId())
                .thenReturn(Optional.of(10L));

        when(orderRepository.findBySellerIdOrderByOrderIdDesc(
                10L,
                PageRequest.of(0, 10)
        )).thenReturn(new PageImpl<>(List.of(order)));

        // when
        SellerOrderPageResult result =
                orderService.getSellerOrders(null, 0, 10);

        // then
        assertThat(result.content()).hasSize(1);

        var summary = result.content().get(0);
        assertThat(summary.orderId()).isEqualTo(101L);
        assertThat(summary.dropId()).isEqualTo(7L);
        assertThat(summary.buyerName()).isEqualTo("김구매");
        assertThat(summary.orderState()).isEqualTo(OrderState.PAID);
    }

    @Test
    @DisplayName("orderState 필터가 있으면 해당 상태만 조회한다")
    void getSellerOrders_withStateFilter() {
        // given
        Order order = createOrder(102L, 5L, 10L, 7L);
        order.confirm();

        when(sellerPort.getCurrentSellerId())
                .thenReturn(Optional.of(10L));

        when(orderRepository.findBySellerIdAndOrderStateOrderByOrderIdDesc(
                10L,
                OrderState.CONFIRMED,
                PageRequest.of(0, 10)
        )).thenReturn(new PageImpl<>(List.of(order)));

        // when
        SellerOrderPageResult result =
                orderService.getSellerOrders("CONFIRMED", 0, 10);

        // then
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).orderState())
                .isEqualTo(OrderState.CONFIRMED);
    }

    @Test
    @DisplayName("승인된 판매자가 아니면 접근이 거부된다")
    void rejectNonSeller() {
        // given
        when(sellerPort.getCurrentSellerId())
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                orderService.getSellerOrders(null, 0, 10)
        ).isInstanceOf(BusinessException.class);

        verifyNoInteractions(orderRepository);
    }

    @Test
    @DisplayName("주문 상세의 연락처는 sellerId 가 아니라 판매자의 memberId 로 조회한다")
    void getOrderDetail_resolvesPhoneByMemberId() {
        // given — sellerId(10)와 판매자 계정의 memberId(20)는 서로 다른 값이다
        Order order = createOrder(101L, 5L, 10L, 7L);

        when(orderRepository.findById(101L))
                .thenReturn(Optional.of(order));
        when(sellerPort.findSeller(10L))
                .thenReturn(Optional.of(new SellerInfo(
                        10L,
                        20L,
                        "이세종 베이커리",
                        "서울시 강남구 테헤란로 1"
                )));
        when(memberPort.getMember(20L))
                .thenReturn(ApiResponse.ok(new MemberInfo("이세종", "010-1234-5678")));

        // when
        OrderDetailResult result =
                orderService.getOrderDetail(5L, 101L);

        // then — 주소·연락처는 지금 값, 상호명은 주문 시점 스냅샷
        assertThat(result.seller().address())
                .isEqualTo("서울시 강남구 테헤란로 1");
        assertThat(result.seller().phoneNumber())
                .isEqualTo("010-1234-5678");
        assertThat(result.seller().sellerName())
                .isEqualTo("이세종 베이커리");

        //sellerId 로 회원을 조회하면 엉뚱한 사람의 번호가 나간다
        verify(memberPort, never()).getMember(10L);
    }

    @Test
    @DisplayName("판매자 조회에 실패해도 상호명 스냅샷은 남는다")
    void getOrderDetail_sellerMissing() {
        // given
        Order order = createOrder(102L, 5L, 10L, 7L);

        when(orderRepository.findById(102L))
                .thenReturn(Optional.of(order));
        when(sellerPort.findSeller(10L))
                .thenReturn(Optional.empty());

        // when
        OrderDetailResult result =
                orderService.getOrderDetail(5L, 102L);

        // then
        assertThat(result.seller().sellerName()).isEqualTo("이세종 베이커리");
        assertThat(result.seller().address()).isNull();
        assertThat(result.seller().phoneNumber()).isNull();
        verifyNoInteractions(memberPort);
    }

    @Test
    @DisplayName("주문 생성 시 판매자 상호명을 스냅샷한다")
    void create_snapshotsSellerName() {
        // given
        Long buyerId = 5L;
        Long sellerId = 10L;
        Long dropId = 7L;

        when(cartPort.findCart(buyerId)).thenReturn(Optional.of(new CartInfo(
                buyerId,
                dropId,
                2,
                LocalDate.of(2026, 7, 17),
                LocalDateTime.now().plusMinutes(10)
        )));
        when(dropPort.getDrop(dropId))
                .thenReturn(new DropInfo(dropId, sellerId, "시그니처 소금빵", 2500));
        when(sellerPort.findSeller(sellerId))
                .thenReturn(Optional.of(new SellerInfo(sellerId, 20L, "이세종 베이커리", "서울시 강남구")));
        when(memberPort.getMember(buyerId))
                .thenReturn(ApiResponse.ok(new MemberInfo("김구매", "010-0000-0000")));

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentPort.pay(any(), any(), any(), any()))
                .thenReturn(new PaymentResult("SUCCESS", null));
        when(paymentPort.getBalance(buyerId))
                .thenReturn(new BalanceInfo(buyerId, new BigDecimal("3000")));

        // when
        orderService.create(buyerId, true);

        // then
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getSellerName()).isEqualTo("이세종 베이커리");
        assertThat(captor.getValue().getBuyerName()).isEqualTo("김구매");

        //연락처는 스냅샷하지 않으므로 생성 시점에 판매자 회원을 조회하지 않는다
        verify(memberPort, never()).getMember(20L);
    }

    private Order createOrder(
            Long orderId,
            Long memberId,
            Long sellerId,
            Long dropId
    ) {
        Order order = Order.create(
                memberId,
                sellerId,
                "김구매",
                "이세종 베이커리",
                LocalDate.of(2026, 7, 17),
                new BigDecimal("5000")
        );

        order.addItem(
                OrderItem.create(
                        dropId,
                        2,
                        new BigDecimal("2500"),
                        "시그니처 소금빵"
                )
        );

        ReflectionTestUtils.setField(order, "orderId", orderId);

        return order;
    }
}
