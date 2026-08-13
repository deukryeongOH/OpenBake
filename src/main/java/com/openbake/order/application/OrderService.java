package com.openbake.order.application;

import com.openbake.cart.domain.Cart;
import com.openbake.cart.domain.CartItem;
import com.openbake.cart.domain.CartRepository;
import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
// member-service 모듈이 분리됨에 따라 member-service 내에 Repository를 직접 참조하던 것을 FeignClient 사용
import com.openbake.order.application.port.MemberPort;
import com.openbake.order.domain.Order;
import com.openbake.order.domain.OrderItem;
import com.openbake.order.domain.OrderRepository;
import com.openbake.order.domain.OrderState;
import com.openbake.drop.application.service.DropLockService;
import com.openbake.drop.domain.entity.Drop;
import com.openbake.drop.domain.repository.DropRepository;
import com.openbake.order.application.port.PaymentPort;
import com.openbake.order.application.port.dto.PaymentResult;
import com.openbake.product.domain.Product;
import com.openbake.product.domain.ProductRepository;
import com.openbake.seller.application.CurrentSellerProvider;
import com.openbake.seller.domain.Seller;
import com.openbake.seller.domain.SellerRepository;
import com.openbake.settlement.application.event.PurchaseConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final PaymentPort paymentPort;
    private final ProductRepository productRepository;
    private final CurrentSellerProvider currentSellerProvider;
    private final SellerRepository sellerRepository;
    //판매자 판매내역 조회 시 구매자 표시 이름 + 판매자 전화번호 조회용
    private final MemberPort memberPort;
    //재고 복구 요청(취소 시). 차감은 order 가 하지 않고 복구만 drop 에 동기 호출한다.
    private final DropLockService dropLockService;
    //주문 생성 스냅샷 소스(판매자/가격/상품명) 조회용.
    private final DropRepository dropRepository;
    //결제 실패 시 재고 복구·장바구니 정리(별도 트랜잭션).
    private final OrderReservationReleaser reservationReleaser;
    //구매확정 이벤트 발행. 정산 리스너가 커밋 후 별도 트랜잭션에서 받는다.
    private final ApplicationEventPublisher eventPublisher;

    //목록 페이지 크기 상한. 명세서 기준 최대 50.
    private static final int MAX_PAGE_SIZE = 50;

    //자동 구매확정 기준 일수. 결제 완료 후 이 일수가 지나면 자동 확정한다(정책값).
    @Value("${openbake.order.auto-confirm-days:1}")
    private long autoConfirmDays;

    /**
     * 주문 생성(결제). 주문 대상은 본문이 아니라 회원의 장바구니에서 읽는다.
     * 재고는 장바구니 생성 시점에 이미 선점됐으므로 여기서는 건드리지 않는다.
     * 주문 저장 → 결제 → 장바구니 삭제가 모두 한 트랜잭션이라, 결제가 실패하면 전부 롤백된다.
     */
    @Transactional
    public OrderCreateResult create(Long memberId, Boolean termsAgreed) {
        LocalDateTime now = LocalDateTime.now();

        // 1. 약관 동의 확인
        if (termsAgreed == null || !termsAgreed) {
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

        // 5. 스냅샷 값 — 장바구니의 dropId 로 드롭을 조회해 주문 시점 값을 복사한다.
        //    가격/상품명/판매자는 주문 시점에 고정(스냅샷)되어 이후 드롭이 바뀌어도 유지된다.
        Drop drop = dropRepository.findById(dropId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DROP_NOT_FOUND));
        Product product = productRepository.findById(drop.getProductId()).orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        Long sellerId = product.getSellerId();
        //가격은 drop 이 int 라 BigDecimal 로 변환한다.
        BigDecimal priceSnapshot = BigDecimal.valueOf(product.getPrice());
        String dropNameSnapshot = product.getName();
        BigDecimal totalAmount = priceSnapshot.multiply(BigDecimal.valueOf(quantity));

        // 구매자 이름 스냅샷
        String buyerName = memberPort.getMember(memberId).data().name();

        // 판매자 전화번호 스냅샷
        String sellerPhoneNumber = memberPort.getMember(sellerId).data().phoneNumber();

        // 6. 주문 생성 — 결제에 넘길 orderId 가 필요하므로 즉시 flush 해 PK 를 확보한다.
        Order order = Order.create(memberId, sellerId, buyerName, sellerPhoneNumber, pickupDate, totalAmount);
        order.addItem(OrderItem.create(dropId, quantity, priceSnapshot, dropNameSnapshot));
        Order saved = orderRepository.save(order);

        // 7. 결제 — 예치금 차감. 실패(잔액 부족 등) 시 담기 때 선점된 재고를 복구하고 장바구니를 정리한다.
        //    재고 차감은 담기 시점에 drop 이 이미 커밋했으므로, 여기 트랜잭션 롤백만으로는 안 돌아온다.
        //    그래서 별도 트랜잭션(REQUIRES_NEW)으로 복구·정리한 뒤 예외를 다시 던져 주문/결제는 롤백한다.
        try {
            String idempotencyKey = "order-" + saved.getOrderId() + "-pay";
            PaymentResult payResult = paymentPort.pay(idempotencyKey, saved.getOrderId(), memberId, totalAmount);
            if (!payResult.isSuccess()) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
            }
        } catch (BusinessException e) {
            reservationReleaser.releaseOnPaymentFailure(memberId);
            throw e;
        }

        // 8. 장바구니 삭제 — 결제 성공 시. 재고는 복구하지 않는다(주문이 선점을 확정한 것이므로).
        cartRepository.delete(cart);

        // 9. 결제 후 잔액 조회
        BigDecimal balanceAfter = paymentPort.getBalance(memberId).balance();

        return new OrderCreateResult(
                saved.getOrderId(),
                saved.getOrderState(),
                totalAmount,
                balanceAfter,
                saved.getPaidAt()
        );
    }

    /**
     * 주문 목록 조회(본인, 최신순). orderState 가 있으면 해당 상태만 필터한다.
     */
    @Transactional(readOnly = true)
    public OrderPageResult getOrders(Long memberId, String orderState, int page, int size) {
        int cappedSize = Math.min(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(page, cappedSize);

        Page<Order> orders;
        if (orderState == null || orderState.isBlank()) {
            orders = orderRepository.findByMemberIdOrderByOrderIdDesc(memberId, pageable);
        } else {
            OrderState state = parseOrderState(orderState);
            orders = orderRepository.findByMemberIdAndOrderStateOrderByOrderIdDesc(memberId, state, pageable);
        }

        return new OrderPageResult(
                orders.map(this::toSummary).getContent(),
                orders.getNumber(),
                orders.getSize(),
                orders.getTotalElements(),
                orders.getTotalPages()
        );
    }

    /**
     * 판매자 본인 판매내역 목록 조회(최신순). orderState 가 있으면 해당 상태만 필터한다.
     * 판매자 판정은 confirm() 과 동일하게 로그인 계정의 sellerId 존재 여부로 한다(미등록 계정은 403).
     */
    @Transactional(readOnly = true)
    public SellerOrderPageResult getSellerOrders(String orderState, int page, int size) {
        Long sellerId = currentSellerProvider.getSellerId()
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCESS_DENIED));

        int cappedSize = Math.min(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(page, cappedSize);

        Page<Order> orders;
        if (orderState == null || orderState.isBlank()) {
            orders = orderRepository.findBySellerIdOrderByOrderIdDesc(sellerId, pageable);
        } else {
            OrderState state = parseOrderState(orderState);
            orders = orderRepository.findBySellerIdAndOrderStateOrderByOrderIdDesc(sellerId, state, pageable);
        }

        return new SellerOrderPageResult(
                orders.map(this::toSellerSummary).getContent(),
                orders.getNumber(),
                orders.getSize(),
                orders.getTotalElements(),
                orders.getTotalPages()
        );
    }

    /**
     * 주문 상세 조회. 본인 주문만 볼 수 있다.
     */
    @Transactional(readOnly = true)
    public OrderDetailResult getOrderDetail(Long memberId, Long orderId) {
        Order order = getOwnedOrder(memberId, orderId);
        OrderItem item = order.getOrderItem();

        OrderDetailResult.OrderItemInfo itemInfo = new OrderDetailResult.OrderItemInfo(
                item.getDropId(),
                item.getDropNameSnapshot(),
                item.getPriceSnapshot(),
                item.getQuantity()
        );

        // order.sellerId()를 넘기던 것을 order에 스냅샷한 phoneNumber도 같이 넘기기 위해 order 전체를 넘김
        OrderDetailResult.SellerInfo sellerInfo = resolveSellerInfo(order);

        return new OrderDetailResult(
                order.getOrderId(),
                itemInfo,
                sellerInfo,
                order.getPickupDate(),
                order.getOrderState(),
                order.getPaidAt(),
                order.getConfirmAt(),
                order.getCancelAt()
        );
    }

    /**
     * 주문 취소. 본인 주문만. 전액 환불 + 재고 복구를 같은 트랜잭션에서 처리한다.
     */
    @Transactional
    public OrderCancelResult cancel(Long memberId, Long orderId) {
        Order order = getOwnedOrder(memberId, orderId);

        // 상태 전이 — PAID 가 아니면 ORDER_NOT_CANCELABLE
        order.cancel();

        // 예치금 전액 환불
        String idempotencyKey = "order-" + orderId + "-refund";
        PaymentResult refundResult = paymentPort.refund(idempotencyKey, orderId);
        if (!refundResult.isSuccess()) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
        }

        // 재고 복구 — 선점했던 수량을 drop 에 되돌린다(동기 호출). 재고 소유는 drop 이다.
        // 차감은 order 가 하지 않았고(담기 시점에 drop 이 함), 복구만 예외 상황에서 요청한다.
        OrderItem item = order.getOrderItem();
        dropLockService.rollbackStock(item.getDropId(), memberId);

        BigDecimal balanceAfter = paymentPort.getBalance(memberId).balance();

        return new OrderCancelResult(
                order.getOrderId(),
                order.getOrderState(),
                order.getTotalAmount(),
                balanceAfter,
                order.getCancelAt()
        );
    }

    /**
     * 구매 확정(판매자). 해당 주문의 판매자만 확정할 수 있다.
     * 주문 상태와 결제 상태를 같은 트랜잭션에서 함께 바꾼다.
     */
    @Transactional
    public OrderConfirmResult confirm(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        // 판매자 판정 — 로그인한 판매자의 sellerId 와 주문의 sellerId 가 같아야 한다.
        //   member 에 seller role 이 없어 role 로는 판정할 수 없다.
        Long sellerId = currentSellerProvider.getSellerId()
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCESS_DENIED));
        if (!sellerId.equals(order.getSellerId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        confirmOrder(order);

        return OrderConfirmResult.from(order);
    }

    /**
     * 자동 구매확정 배치. 결제 완료 후 지정 일수가 지나도록 확정되지 않은(PAID) 주문을 확정한다.
     * 정산 누락을 막는 안전망이다. 스케줄러가 주문 단위로 이 메서드를 호출한다(주문별 트랜잭션).
     */
    @Transactional
    public void autoConfirm(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        //조회 후 확정 사이에 판매자가 수동 확정/취소했을 수 있다. PAID 가 아니면 건너뛴다(멱등).
        if (order.getOrderState() != OrderState.PAID) {
            return;
        }
        confirmOrder(order);
    }

    /**
     * 자동 확정 대상 주문 ID 조회. 결제 완료 시각이 (현재 - N일) 이전인 PAID 주문.
     */
    @Transactional(readOnly = true)
    public List<Long> findAutoConfirmTargetIds() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(autoConfirmDays);
        return orderRepository.findByOrderStateAndPaidAtBefore(OrderState.PAID, cutoff)
                .stream()
                .map(Order::getOrderId)
                .toList();
    }

    /**
     * 구매확정 공통 처리. 수동 확정과 자동 확정이 공유한다.
     * 주문 상태 전이 + 결제 상태 전이 + 정산 이벤트 발행을 같은 트랜잭션에서 수행한다.
     */
    private void confirmOrder(Order order) {
        // 상태 전이 — PAID 가 아니면 ORDER_NOT_CONFIRMABLE
        order.confirm();

        // 결제 상태도 CONFIRMED 로 전이한다.
        PaymentResult confirmResult = paymentPort.confirm(order.getOrderId());
        if (!confirmResult.isSuccess()) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
        }

        // 정산으로 구매확정 이벤트를 발행한다.
        publishPurchaseConfirmed(order);
    }

    /**
     * 구매확정 이벤트를 정산으로 발행한다.
     *
     * 확정 트랜잭션 안에서 발행하지만 정산 리스너가 AFTER_COMMIT 이라,
     * 확정이 롤백되면 정산은 실행되지 않고 커밋된 뒤에야 별도 트랜잭션에서 처리된다.
     */
    private void publishPurchaseConfirmed(Order order) {
        OrderItem item = order.getOrderItem();

        eventPublisher.publishEvent(new PurchaseConfirmedEvent(
                //정산이 중복 수신을 걸러내는 멱등 키.
                UUID.randomUUID().toString(),
                order.getOrderId(),
                item.getOrderItemId(),
                order.getSellerId(),
                item.getDropId(),
                item.getDropNameSnapshot(),
                item.getQuantity(),
                order.getTotalAmount(),
                //confirmAt(LocalDateTime) → 정산이 요구하는 OffsetDateTime 으로 변환(시스템 존 기준).
                order.getConfirmAt().atZone(ZoneId.systemDefault()).toOffsetDateTime()
        ));
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
    private OrderSummaryResult toSummary(Order order) {
        OrderItem item = order.getOrderItem();
        return new OrderSummaryResult(
                order.getOrderId(),
                item.getDropNameSnapshot(),
                resolveSellerName(order.getSellerId()),
                item.getQuantity(),
                order.getTotalAmount(),
                order.getOrderState(),
                order.getPickupDate(),
                order.getPaidAt()
        );
    }

    //판매자 상호명 조회. 판매자가 없으면 null.
    private String resolveSellerName(Long sellerId) {
        return sellerRepository.findById(sellerId)
                .map(Seller::getBakeryName)
                .orElse(null);
    }

    //주문 상세용 판매자 정보 조립. 지도 보기/전화하기 버튼을 위해 사업장 주소·연락처를 포함한다.
    //연락처는 seller 가 직접 갖지 않고 연결된 member 의 phoneNumber 를 사용한다.
    private OrderDetailResult.SellerInfo resolveSellerInfo(Order order) {
        return sellerRepository.findById(order.getSellerId())
                .map(seller -> new OrderDetailResult.SellerInfo(
                        order.getSellerId(),
                        seller.getBakeryName(),
                        seller.getBusinessAddress(),
                        order.getSellerPhoneNumber()))
                .orElse(new OrderDetailResult.SellerInfo(order.getSellerId(), null, null, null));
    }

    //판매자 판매내역 목록 항목 조립. dropId/dropName 은 스냅샷, buyerName 은 member 조회.
    private SellerOrderSummaryResult toSellerSummary(Order order) {
        OrderItem item = order.getOrderItem();
        return new SellerOrderSummaryResult(
                order.getOrderId(),
                item.getDropId(),
                item.getDropNameSnapshot(),
                order.getBuyerName(),
                item.getQuantity(),
                order.getTotalAmount(),
                order.getOrderState(),
                order.getPickupDate(),
                order.getPaidAt(),
                order.getConfirmAt(),
                order.getCancelAt()
        );
    }
}
