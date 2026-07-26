package com.openbake.order.application;

import com.openbake.cart.domain.Cart;
import com.openbake.cart.domain.CartItem;
import com.openbake.cart.domain.CartRepository;
import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.order.domain.Order;
import com.openbake.order.domain.OrderItem;
import com.openbake.order.domain.OrderRepository;
import com.openbake.order.domain.OrderState;
import com.openbake.order.presentation.dto.OrderCancelResponse;
import com.openbake.order.presentation.dto.OrderConfirmResponse;
import com.openbake.order.presentation.dto.OrderCreateRequest;
import com.openbake.order.presentation.dto.OrderCreateResponse;
import com.openbake.order.presentation.dto.OrderDetailResponse;
import com.openbake.order.presentation.dto.OrderPageResponse;
import com.openbake.order.presentation.dto.OrderSummaryResponse;
import com.openbake.payment.application.DepositService;
import com.openbake.payment.application.PaymentService;
import com.openbake.seller.application.CurrentSellerProvider;
import com.openbake.seller.domain.Seller;
import com.openbake.seller.domain.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final PaymentService paymentService;
    private final DepositService depositService;
    private final CurrentSellerProvider currentSellerProvider;
    private final SellerRepository sellerRepository;

    //목록 페이지 크기 상한. 명세서 기준 최대 50.
    private static final int MAX_PAGE_SIZE = 50;

    // TODO(drop): 드롭 조회 포트가 없어 스냅샷 소스를 임시 상수로 둔다.
    //   포트가 생기면 dropId 로 조회해 sellerId/상품명/가격을 읽어 교체한다.
    private static final Long STUB_SELLER_ID = 1L;
    private static final BigDecimal STUB_PRICE = BigDecimal.valueOf(10000);
    private static final String STUB_DROP_NAME = "임시 상품명";

    /**
     * 주문 생성(결제). 주문 대상은 본문이 아니라 회원의 장바구니에서 읽는다.
     * 재고는 장바구니 생성 시점에 이미 선점됐으므로 여기서는 건드리지 않는다.
     * 주문 저장 → 결제 → 장바구니 삭제가 모두 한 트랜잭션이라, 결제가 실패하면 전부 롤백된다.
     */
    @Transactional
    public OrderCreateResponse create(Long memberId, OrderCreateRequest request) {
        LocalDateTime now = LocalDateTime.now();

        // 1. 약관 동의 확인
        if (request.getTermsAgreed() == null || !request.getTermsAgreed()) {
            throw new BusinessException(ErrorCode.TERMS_NOT_AGREED);
        }

        // 2. 장바구니 조회 — 없으면 CART_NOT_FOUND
        Cart cart = cartRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_NOT_FOUND));

        // 3. 만료 확인 — 선점 재고가 이미 복구됐을 수 있는 구간
        if (cart.isExpired(now)) {
            throw new BusinessException(ErrorCode.CART_EXPIRED);
        }

        // 4. 픽업 날짜 선택 확인
        LocalDate pickupDate = cart.getPickupDate();
        if (pickupDate == null) {
            throw new BusinessException(ErrorCode.PICKUP_DATE_NOT_SELECTED);
        }

        CartItem item = cart.getItems();
        Long dropId = item.getDropId();
        int quantity = item.getQuantity();

        // 5. 스냅샷 값 — TODO(drop) 로 임시 상수. totalAmount = 단가 × 수량
        Long sellerId = STUB_SELLER_ID;
        BigDecimal priceSnapshot = STUB_PRICE;
        String dropNameSnapshot = STUB_DROP_NAME;
        BigDecimal totalAmount = priceSnapshot.multiply(BigDecimal.valueOf(quantity));

        // 6. 주문 생성 — 결제에 넘길 orderId 가 필요하므로 즉시 flush 해 PK 를 확보한다.
        Order order = Order.create(memberId, sellerId, pickupDate, totalAmount);
        order.addItem(OrderItem.create(dropId, quantity, priceSnapshot, dropNameSnapshot));
        Order saved = orderRepository.saveAndFlush(order);

        // 7. 결제 — 예치금 차감. 잔액 부족 시 여기서 INSUFFICIENT_BALANCE 로 터진다(롤백).
        paymentService.pay(saved.getOrderId(), memberId, totalAmount);

        // 8. 장바구니 삭제 — 재고는 복구하지 않는다(주문이 선점을 확정한 것이므로).
        cartRepository.delete(cart);

        // 9. 결제 후 잔액 조회
        BigDecimal balanceAfter = depositService.getBalance(memberId).balance();

        return OrderCreateResponse.builder()
                .orderId(saved.getOrderId())
                .orderState(saved.getOrderState())
                .totalAmount(totalAmount)
                .balanceAfter(balanceAfter)
                .paidAt(saved.getPaidAt())
                .build();
    }

    /**
     * 주문 목록 조회(본인, 최신순). orderState 가 있으면 해당 상태만 필터한다.
     */
    @Transactional(readOnly = true)
    public OrderPageResponse getOrders(Long memberId, String orderState, int page, int size) {
        int cappedSize = Math.min(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(page, cappedSize);

        Page<Order> orders;
        if (orderState == null || orderState.isBlank()) {
            orders = orderRepository.findByMemberIdOrderByOrderIdDesc(memberId, pageable);
        } else {
            OrderState state = parseOrderState(orderState);
            orders = orderRepository.findByMemberIdAndOrderStateOrderByOrderIdDesc(memberId, state, pageable);
        }

        return OrderPageResponse.builder()
                .content(orders.map(this::toSummary).getContent())
                .page(orders.getNumber())
                .size(orders.getSize())
                .totalElements(orders.getTotalElements())
                .totalPages(orders.getTotalPages())
                .build();
    }

    /**
     * 주문 상세 조회. 본인 주문만 볼 수 있다.
     */
    @Transactional(readOnly = true)
    public OrderDetailResponse getOrderDetail(Long memberId, Long orderId) {
        Order order = getOwnedOrder(memberId, orderId);
        OrderItem item = order.getOrderItem();

        // TODO(drop): dropCloseAt = drop.dropEnd 를 조회해야 한다. 포트가 없어 지금은 null.
        //   cancelable 도 원래 (PAID && now < dropCloseAt) 인데, 마감 시각을 못 읽어
        //   당분간 PAID 여부만으로 판정한다.
        LocalDateTime dropCloseAt = null;
        boolean cancelable = order.getOrderState() == OrderState.PAID;

        OrderDetailResponse.OrderItemInfo itemInfo = OrderDetailResponse.OrderItemInfo.builder()
                .dropId(item.getDropId())
                .dropName(item.getDropNameSnapshot())
                .price(item.getPriceSnapshot())
                .quantity(item.getQuantity())
                .build();

        OrderDetailResponse.SellerInfo sellerInfo = OrderDetailResponse.SellerInfo.builder()
                .sellerId(order.getSellerId())
                .sellerName(resolveSellerName(order.getSellerId()))
                .build();

        return OrderDetailResponse.builder()
                .orderId(order.getOrderId())
                .orderItem(itemInfo)
                .seller(sellerInfo)
                .pickupDate(order.getPickupDate())
                .dropCloseAt(dropCloseAt)
                .cancelable(cancelable)
                .orderState(order.getOrderState())
                .paidAt(order.getPaidAt())
                .confirmedAt(order.getConfirmAt())
                .canceledAt(order.getCancelAt())
                .build();
    }

    /**
     * 주문 취소. 본인 주문만. 전액 환불 + 재고 복구를 같은 트랜잭션에서 처리한다.
     */
    @Transactional
    public OrderCancelResponse cancel(Long memberId, Long orderId) {
        Order order = getOwnedOrder(memberId, orderId);

        // TODO(drop): dropCloseAt 을 읽어 now >= dropCloseAt 이면 DROP_ALREADY_CLOSED 로 막아야 한다.
        //   마감 시각 조회 포트가 없어 지금은 상태(PAID)만으로 판정한다.

        // 상태 전이 — PAID 가 아니면 ORDER_NOT_CANCELABLE
        order.cancel();

        // 예치금 전액 환불
        paymentService.refund(orderId);

        // TODO(drop): 재고 복구 — 선점했던 수량을 되돌린다.

        BigDecimal balanceAfter = depositService.getBalance(memberId).balance();

        return OrderCancelResponse.builder()
                .orderId(order.getOrderId())
                .orderState(order.getOrderState())
                .refundAmount(order.getTotalAmount())
                .balanceAfter(balanceAfter)
                .canceledAt(order.getCancelAt())
                .build();
    }

    /**
     * 구매 확정(판매자). 해당 주문의 판매자만 확정할 수 있다.
     * 주문 상태와 결제 상태를 같은 트랜잭션에서 함께 바꾼다.
     */
    @Transactional
    public OrderConfirmResponse confirm(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        // 판매자 판정 — 로그인한 판매자의 sellerId 와 주문의 sellerId 가 같아야 한다.
        //   member 에 seller role 이 없어 role 로는 판정할 수 없다.
        Long sellerId = currentSellerProvider.getSellerId()
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCESS_DENIED));
        if (!sellerId.equals(order.getSellerId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        // 상태 전이 — PAID 가 아니면 ORDER_NOT_CONFIRMABLE(배치 자동확정과 중복 요청 방어)
        order.confirm();

        // 결제 상태도 CONFIRMED 로 전이한다.
        paymentService.confirmPayment(orderId);

        // TODO(settlement): 구매확정 Outbox 이벤트 발행.
        //   commissionRateSnapshot 소스가 미정이라 별도 작업으로 분리한다.

        return OrderConfirmResponse.builder()
                .orderId(order.getOrderId())
                .orderState(order.getOrderState())
                .confirmedAt(order.getConfirmAt())
                .build();
    }

    //본인 주문만 반환. 없으면 ORDER_NOT_FOUND, 타인 주문이면 ACCESS_DENIED(403).
    private Order getOwnedOrder(Long memberId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return order;
    }

    //정의되지 않은 상태값이면 INVALID_ORDER_STATE.
    private OrderState parseOrderState(String value) {
        try {
            return OrderState.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATE);
        }
    }

    //목록 항목 조립. dropName 은 스냅샷, sellerName 은 seller 조회.
    private OrderSummaryResponse toSummary(Order order) {
        OrderItem item = order.getOrderItem();
        return OrderSummaryResponse.builder()
                .orderId(order.getOrderId())
                .dropName(item.getDropNameSnapshot())
                .sellerName(resolveSellerName(order.getSellerId()))
                .quantity(item.getQuantity())
                .totalAmount(order.getTotalAmount())
                .orderState(order.getOrderState())
                .pickupDate(order.getPickupDate())
                .paidAt(order.getPaidAt())
                .build();
    }

    //판매자 상호명 조회. 판매자가 없으면 null.
    private String resolveSellerName(Long sellerId) {
        return sellerRepository.findById(sellerId)
                .map(Seller::getBakeryName)
                .orElse(null);
    }
}
