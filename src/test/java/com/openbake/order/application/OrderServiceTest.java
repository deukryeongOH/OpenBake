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
import com.openbake.order.presentation.dto.OrderDetailResponse;
import com.openbake.order.presentation.dto.SellerOrderPageResponse;
import com.openbake.payment.application.DepositService;
import com.openbake.payment.application.PaymentService;
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
    private PaymentService paymentService;
    @Mock
    private DepositService depositService;
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
                paymentService,
                depositService,
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
        SellerOrderPageResponse response =
                orderService.getSellerOrders(null, 0, 10);

        // then
        assertThat(response.getContent()).hasSize(1);

        var summary = response.getContent().get(0);
        assertThat(summary.getOrderId()).isEqualTo(101L);
        assertThat(summary.getDropId()).isEqualTo(7L);
        assertThat(summary.getBuyerName()).isEqualTo("김구매");
        assertThat(summary.getOrderState()).isEqualTo(OrderState.PAID);
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
        SellerOrderPageResponse response =
                orderService.getSellerOrders("CONFIRMED", 0, 10);

        // then
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getOrderState())
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
        OrderDetailResponse response =
                orderService.getOrderDetail(5L, 101L);

        // then
        assertThat(response.getSeller().getSellerName())
                .isEqualTo("이세종 베이커리");
        assertThat(response.getSeller().getAddress())
                .isEqualTo("서울시 강남구 테헤란로 1");
        assertThat(response.getSeller().getPhoneNumber())
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
        OrderDetailResponse response =
                orderService.getOrderDetail(5L, 102L);

        // then
        assertThat(response.getSeller().getSellerName()).isNull();
        assertThat(response.getSeller().getAddress()).isNull();
        assertThat(response.getSeller().getPhoneNumber()).isNull();
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
