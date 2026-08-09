package com.openbake.order.application;

import com.openbake.cart.domain.CartRepository;
import com.openbake.common.exception.BusinessException;
import com.openbake.drop.application.DropLockService;
import com.openbake.drop.domain.DropRepository;
import com.openbake.member.domain.Member;
import com.openbake.member.domain.MemberRepository;
import com.openbake.order.domain.Order;
import com.openbake.order.domain.OrderItem;
import com.openbake.order.domain.OrderRepository;
import com.openbake.order.domain.OrderState;
import com.openbake.order.infrastructure.PaymentClient;
import com.openbake.seller.application.CurrentSellerProvider;
import com.openbake.seller.domain.Seller;
import com.openbake.seller.domain.SellerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CartRepository cartRepository;
    @Mock
    private PaymentClient paymentClient;
    @Mock
    private CurrentSellerProvider currentSellerProvider;
    @Mock
    private SellerRepository sellerRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private DropLockService dropLockService;
    @Mock
    private DropRepository dropRepository;
    @Mock
    private OrderReservationReleaser reservationReleaser;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderRepository,
                cartRepository,
                paymentClient,
                currentSellerProvider,
                sellerRepository,
                memberRepository,
                dropLockService,
                dropRepository,
                reservationReleaser,
                eventPublisher
        );
    }

    @Test
    @DisplayName("판매자 본인 판매내역을 조회한다")
    void getSellerOrders() {
        // given
        Order order = createOrder(101L, 5L, 10L, 7L);

        when(currentSellerProvider.getSellerId())
                .thenReturn(Optional.of(10L));

        when(orderRepository.findBySellerIdOrderByOrderIdDesc(
                10L,
                PageRequest.of(0, 10)
        )).thenReturn(new PageImpl<>(List.of(order)));

        when(memberRepository.findById(5L))
                .thenReturn(Optional.of(Member.create("김구매", "010-0000-0000")));

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

        when(currentSellerProvider.getSellerId())
                .thenReturn(Optional.of(10L));

        when(orderRepository.findBySellerIdAndOrderStateOrderByOrderIdDesc(
                10L,
                OrderState.CONFIRMED,
                PageRequest.of(0, 10)
        )).thenReturn(new PageImpl<>(List.of(order)));

        when(memberRepository.findById(5L))
                .thenReturn(Optional.of(Member.create("김구매", "010-0000-0000")));

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
        when(currentSellerProvider.getSellerId())
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                orderService.getSellerOrders(null, 0, 10)
        ).isInstanceOf(BusinessException.class);

        verifyNoInteractions(orderRepository);
    }

    @Test
    @DisplayName("주문 상세 조회 시 판매자 주소/연락처를 포함한다")
    void getOrderDetail_includesSellerContact() {
        // given
        Order order = createOrder(101L, 5L, 10L, 7L);

        Seller seller = new Seller(
                20L,
                "이세종 베이커리",
                "123-45-67890",
                "서울시 강남구 테헤란로 1",
                "이세종",
                true,
                "088",
                "1101234567",
                "이세종",
                true
        );

        when(orderRepository.findById(101L))
                .thenReturn(Optional.of(order));
        when(sellerRepository.findById(10L))
                .thenReturn(Optional.of(seller));
        when(memberRepository.findById(20L))
                .thenReturn(Optional.of(Member.create("이세종", "010-1234-5678")));

        // when
        OrderDetailResult result =
                orderService.getOrderDetail(5L, 101L);

        // then
        assertThat(result.seller().sellerName())
                .isEqualTo("이세종 베이커리");
        assertThat(result.seller().address())
                .isEqualTo("서울시 강남구 테헤란로 1");
        assertThat(result.seller().phoneNumber())
                .isEqualTo("010-1234-5678");
    }

    @Test
    @DisplayName("판매자 정보를 찾을 수 없으면 연락처는 null이다")
    void getOrderDetail_sellerMissing() {
        // given
        Order order = createOrder(102L, 5L, 10L, 7L);

        when(orderRepository.findById(102L))
                .thenReturn(Optional.of(order));
        when(sellerRepository.findById(10L))
                .thenReturn(Optional.empty());

        // when
        OrderDetailResult result =
                orderService.getOrderDetail(5L, 102L);

        // then
        assertThat(result.seller().sellerName()).isNull();
        assertThat(result.seller().address()).isNull();
        assertThat(result.seller().phoneNumber()).isNull();
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
