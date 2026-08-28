package com.openbake.order.application;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.order.application.OrderCreateCommand.OrderRoute;
import com.openbake.order.application.OrderSnapshotAssembler.SellerNameCache;
import com.openbake.order.application.port.CartPort;
import com.openbake.order.application.port.DropPort;
import com.openbake.order.application.port.MemberPort;
import com.openbake.order.application.port.PaymentPort;
import com.openbake.order.application.port.ProductPort;
import com.openbake.order.application.port.ReservationPort;
import com.openbake.order.application.port.SellerPort;
import com.openbake.order.application.port.dto.CartItemInfo;
import com.openbake.order.application.port.dto.DropInfo;
import com.openbake.order.application.port.dto.DropReservationInfo;
import com.openbake.order.application.port.dto.PaymentResult;
import com.openbake.order.application.port.dto.ProductInfo;
import com.openbake.order.domain.Order;
import com.openbake.order.domain.OrderItem;
import com.openbake.order.domain.OrderRepository;
import com.openbake.order.domain.OrderState;
import com.openbake.order.domain.SalesType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 주문 생성·조회·취소.
 *
 * 결제는 여기 없다 — {@link OrderPayService} 가 맡는다.
 * 주문 생성과 결제 사이에 <b>사용자 대기 구간(주문서 화면)</b>이 있어 한 요청으로 묶을 수 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartPort cartPort;
    private final ProductPort productPort;
    private final DropPort dropPort;
    private final ReservationPort reservationPort;
    private final PaymentPort paymentPort;
    private final SellerPort sellerPort;
    private final MemberPort memberPort;
    private final OrderSnapshotAssembler assembler;
    private final OrderStockRestorer stockRestorer;

    //목록 페이지 크기 상한. 명세서 기준 최대 50.
    private static final int MAX_PAGE_SIZE = 50;

    //주문서를 붙잡아 둘 수 있는 시간. 만료 배치가 이 시각을 기준으로 정리한다.
    @Value("${openbake.order.reservation-ttl:15m}")
    private Duration reservationTtl;

    /**
     * 주문 생성. <b>재고를 잡지 않고 PENDING 만 만든다.</b>
     *
     * 일반 상품 재고는 결제가 성공한 뒤에 깎는다(개발 속도를 위한 결정).
     * 드롭은 lock-start 에서 이미 깎여 있다.
     *
     * 검증을 먼저 하고 주문을 나중에 만든다 — Saga 앵커는 되돌릴 것이 처음 생기는 지점
     * 직전에 있으면 되고, 검증은 읽기만 하므로 앵커 앞에 둬도 Saga 가 성립한다.
     */
    @Transactional
    public OrderCreateResult create(Long memberId, OrderCreateCommand command) {
        OrderRoute route = command.route();
        Long yieldedOrderId = guardActiveOrder(memberId, route);

        String buyerName = assembler.buyerName(memberId);
        SellerNameCache sellerNames = assembler.newSellerNameCache();
        LocalDateTime expiresAt = LocalDateTime.now().plus(reservationTtl);

        List<OrderItem> items = switch (route) {
            case CART -> itemsFromCart(memberId, command.cartItemIds(), sellerNames);
            case DIRECT_GENERAL -> itemsFromProduct(command, sellerNames);
            case DROP -> itemsFromDrop(memberId, command, sellerNames);
        };

        BigDecimal totalAmount = items.stream()
                .map(OrderItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        SalesType salesType = route == OrderRoute.DROP ? SalesType.DROP : SalesType.GENERAL;
        Order order = Order.createPending(memberId, buyerName, salesType, totalAmount, expiresAt);
        items.forEach(order::addItem);

        Order saved = orderRepository.save(order);

        return new OrderCreateResult(
                saved.getOrderId(),
                saved.getOrderState(),
                saved.getTotalAmount(),
                saved.getReservationExpiresAt(),
                saved.getItems().stream().map(this::toSheetItem).toList(),
                yieldedOrderId
        );
    }

    /**
     * 회원당 진행 중 주문은 1건이다.
     *
     * 막아야 하는 것은 "같은 요청"이 아니라 <b>"동시에 두 건"</b>이라, 요청 내용에서
     * 파생한 키로는 원리적으로 막을 수 없다. 창 두 개가 장바구니 항목을 부분만 겹치게
     * 고르면 내용 해시는 서로 달라 그냥 통과한다.
     *
     * <b>드롭에는 우선권을 준다.</b> 대기열을 통과해 선점한 재고는 다시 만들 수 없지만
     * 일반 상품 주문은 언제든 다시 만들 수 있다. 되돌릴 수 없는 쪽이 이긴다.
     *
     * @return 드롭 우선권으로 만료시킨 기존 주문 ID. 없으면 null
     */
    private Long guardActiveOrder(Long memberId, OrderRoute route) {
        Order active = orderRepository.findByActiveMemberIdForUpdate(memberId).orElse(null);
        if (active == null) {
            return null;
        }

        if (route != OrderRoute.DROP) {
            //409 로 끝내지 않는다. 프론트가 GET /orders/pending 으로 기존 주문을 보여주고
            //"이어서 결제하기 / 취소하고 새로 주문"을 고르게 한다.
            throw new BusinessException(ErrorCode.DUPLICATE_REQUEST);
        }

        log.info("드롭 주문 우선권 — 기존 진행 중 주문을 만료시킨다. orderId={}", active.getOrderId());
        stockRestorer.restorePending(active);
        active.markExpired();
        //슬롯 UNIQUE 때문에 새 주문 INSERT 전에 반납이 먼저 반영돼야 한다.
        orderRepository.save(active);
        return active.getOrderId();
    }

    // ── 경로별 항목 조립 ─────────────────────────────────────────

    /**
     * 경로 1 — 장바구니에서 고른 항목.
     *
     * <b>타입 재검증을 하지 않는다.</b> 장바구니에는 GENERAL 만 담기고(PR005 로 차단),
     * Product.type 은 생성자에서만 대입되고 이후 바뀌지 않는다. 거기에 소유권 검증까지
     * 하므로 타입이 이미 보장된다.
     */
    private List<OrderItem> itemsFromCart(Long memberId, List<Long> cartItemIds, SellerNameCache sellerNames) {
        List<CartItemInfo> cartItems = cartPort.findItemsForOrder(memberId, cartItemIds);

        List<OrderItem> items = new ArrayList<>();
        for (CartItemInfo cartItem : cartItems) {
            ProductInfo product = findProduct(cartItem.productId());
            assembler.validateGeneralOrderable(product, cartItem.quantity(), cartItem.pickUpDate());

            items.add(OrderItem.create(
                    product.productId(),
                    null,
                    cartItem.cartItemId(),
                    cartItem.quantity(),
                    BigDecimal.valueOf(product.price()),
                    product.name(),
                    product.sellerId(),
                    sellerNames.get(product.sellerId()),
                    cartItem.pickUpDate(),
                    product.imageUrl()
            ));
        }
        return items;
    }

    /**
     * 경로 2 — 상품 상세에서 바로 주문.
     *
     * <b>타입 재검증이 필수다.</b> productId 를 날것으로 받으므로, 드롭 상품 id 를 넣어
     * 대기열과 선점을 건너뛰고 사는 것을 막아야 한다.
     */
    private List<OrderItem> itemsFromProduct(OrderCreateCommand command, SellerNameCache sellerNames) {
        ProductInfo product = findProduct(command.productId());
        if (!product.generalType()) {
            throw new BusinessException(ErrorCode.INVALID_PRODUCT_TYPE);
        }
        assembler.validateGeneralOrderable(product, command.quantity(), command.pickUpDate());

        return List.of(OrderItem.create(
                product.productId(),
                null,
                null,
                command.quantity(),
                BigDecimal.valueOf(product.price()),
                product.name(),
                product.sellerId(),
                sellerNames.get(product.sellerId()),
                command.pickUpDate(),
                product.imageUrl()
        ));
    }

    /**
     * 경로 3 — 드롭.
     *
     * 재고는 이미 lock-start 에서 깎였다. 여기서 하는 것은 <b>선점 확인</b>뿐이고,
     * 그것이 곧 살 자격이다. 수량은 클라이언트에서 받지 않고 선점값을 읽는다.
     *
     * 드롭 마감 여부는 보지 않는다 — 주문서를 쓰는 동안 dropEnd 가 지났다고
     * 이미 잡은 재고를 뺏으면 안 된다.
     */
    private List<OrderItem> itemsFromDrop(Long memberId, OrderCreateCommand command, SellerNameCache sellerNames) {
        DropReservationInfo reservation = reservationPort.getReservation(command.dropId(), memberId);
        if (!reservation.reserved()) {
            throw new BusinessException(ErrorCode.STOCK_NOT_RESERVED);
        }

        //선점은 결제 뒤에도 RESERVED 로 남는다. 자격 검사만 믿으면 같은 선점으로 주문을
        //여러 번 만들 수 있고, 그중 하나를 취소하면 이미 결제된 주문의 재고까지 풀린다.
        //"이 선점으로 이미 주문했는가"는 drop 이 아니라 order 가 아는 사실이라 여기서 막는다.
        if (orderRepository.existsLiveDropOrder(memberId, command.dropId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_REQUEST);
        }

        DropInfo drop = dropPort.getDrop(command.dropId());
        assembler.validatePickUpDate(
                drop.pickUpAvailableDates().contains(command.pickUpDate()), command.pickUpDate());

        return List.of(OrderItem.create(
                drop.productId(),
                drop.dropId(),
                null,
                reservation.selectQuantity(),
                BigDecimal.valueOf(drop.price()),
                drop.name(),
                drop.sellerId(),
                sellerNames.get(drop.sellerId()),
                command.pickUpDate(),
                drop.imageUrl()
        ));
    }

    // ── 조회 ────────────────────────────────────────────────────

    /**
     * 주문 목록(본인, 최신순).
     *
     * PENDING 은 진행 중이라 별도 화면이고, FAILED·EXPIRED 는 사용자 입장에서
     * "주문한 적이 없는" 것이므로 목록에 넣지 않는다.
     */
    @Transactional(readOnly = true)
    public OrderPageResult getOrders(Long memberId, String orderState, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
        List<OrderState> states = resolveHistoryStates(orderState);

        Page<Order> orders =
                orderRepository.findByMemberIdAndOrderStateInOrderByOrderIdDesc(memberId, states, pageable);

        return new OrderPageResult(
                orders.map(this::toSummary).getContent(),
                orders.getNumber(),
                orders.getSize(),
                orders.getTotalElements(),
                orders.getTotalPages()
        );
    }

    /**
     * 진행 중(PENDING) 주문 조회.
     *
     * 돌아온 사용자에게 "하던 주문을 마저 하시겠습니까?"를 띄우기 위한 것이고,
     * 중복 주문이 OR006 으로 막혔을 때 프론트가 곧바로 호출하는 화면이기도 하다.
     * 없으면 빈 값 — 예외가 아니다. "진행 중인 주문이 없다"는 정상 상태다.
     */
    @Transactional(readOnly = true)
    public Optional<OrderDetailResult> getPendingOrder(Long memberId) {
        return orderRepository.findByActiveMemberId(memberId).map(this::toDetail);
    }

    @Transactional(readOnly = true)
    public OrderDetailResult getOrderDetail(Long memberId, Long orderId) {
        return toDetail(getOwnedOrder(memberId, orderId));
    }

    /**
     * 판매자 본인 판매내역(최신순).
     *
     * Order.sellerId 가 사라져 항목 조인으로 찾고, 응답에서도 자기 항목만 남긴다.
     */
    @Transactional(readOnly = true)
    public SellerOrderPageResult getSellerOrders(String orderState, int page, int size) {
        Long sellerId = sellerPort.getCurrentSellerId()
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCESS_DENIED));

        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
        List<OrderState> states = resolveHistoryStates(orderState);

        Page<Order> orders = orderRepository.findBySellerId(sellerId, states, pageable);

        return new SellerOrderPageResult(
                orders.map(order -> toSellerSummary(order, sellerId)).getContent(),
                orders.getNumber(),
                orders.getSize(),
                orders.getTotalElements(),
                orders.getTotalPages()
        );
    }

    // ── 취소 ────────────────────────────────────────────────────

    /**
     * 취소. <b>하나의 API 가 상태로 갈린다.</b>
     *
     * 프론트는 지금 상태를 몰라도 되고, 서버가 결제 전인지 후인지 보고 처리한다.
     * 갈라야 할 축은 "만료냐 취소냐"가 아니라 <b>"결제 전 종료냐 결제 후 취소냐"</b>다 —
     * 환불 유무·복구 대상·주문 내역 노출이 이 축으로 한꺼번에 갈린다.
     */
    @Transactional
    public OrderCancelResult cancel(Long memberId, Long orderId) {
        //결제 성공 반영과 동시에 실행될 수 있으므로 주문 행을 잠근다.
        //PENDING 취소는 로컬 처리뿐이고, PAID 취소는 기존 구조대로 환불 호출까지 이 트랜잭션에 포함된다.
        Order order = getOwnedOrderForUpdate(memberId, orderId);

        return switch (order.getOrderState()) {
            case PENDING -> cancelBeforePayment(order);
            case PAID -> cancelAfterPayment(order, memberId);
            default -> throw new BusinessException(ErrorCode.ORDER_NOT_CANCELABLE);
        };
    }

    /**
     * 결제 전 취소 → EXPIRED. 환불이 없고 주문 내역에도 뜨지 않는다.
     *
     * ABANDONED 같은 상태를 따로 만들지 않는 이유는 후속 처리가 만료와 <b>완전히 같기</b>
     * 때문이다. 이름이 사실과 다른 문제는 파생으로 구분한다 —
     * expiredAt 이 reservationExpiresAt 보다 이르면 사용자가 직접 나간 것이다.
     */
    private OrderCancelResult cancelBeforePayment(Order order) {
        Long orderId = order.getOrderId();
        Long memberId = order.getMemberId();

        stockRestorer.restorePending(order);

        //드롭 복구는 재고 벌크 UPDATE 를 타 영속성 컨텍스트를 비운다. 다시 읽어야 전이가 저장된다.
        Order managed = getOwnedOrderForUpdate(memberId, orderId);
        managed.markExpired();

        return new OrderCancelResult(
                managed.getOrderId(),
                managed.getOrderState(),
                BigDecimal.ZERO,
                null,
                managed.getExpiredAt()
        );
    }

    /**
     * 결제 후 취소 → CANCELED. 전액 환불 + 재고 복구.
     *
     * <b>검증을 맨 앞에 둔다.</b> 이미 CANCELED 거나 항목이 하나라도 확정됐으면 여기서
     * 막힌다 — 확정된 항목은 정산이 이미 나가서 되돌릴 수 없다.
     * 검증이 환불 뒤로 밀리면 <b>돈만 돌려주고 취소는 실패</b>할 수 있는데,
     * 환불은 외부 서비스라 트랜잭션 롤백으로 되돌아오지 않는다.
     *
     * ⚠️ 상태 전이는 재고 복구 <b>뒤에</b> 한다. 복구가 재고 벌크 UPDATE 를 타
     * 영속성 컨텍스트를 비우기 때문에, 그 전에 바꿔 두면 아직 flush 되지 않은 변경이
     * 통째로 버려진다({@code flushAutomatically} 는 기본값이 false 다).
     */
    private OrderCancelResult cancelAfterPayment(Order order, Long memberId) {
        order.validateCancelable();

        PaymentResult refund = paymentPort.refund(
                "order-" + order.getOrderId() + "-refund",
                order.getOrderId(),
                memberId,
                order.getTotalAmount());
        if (!refund.isSuccess()) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
        }

        stockRestorer.restorePaid(order);

        order = getOwnedOrderForUpdate(memberId, order.getOrderId());
        order.cancel();

        return new OrderCancelResult(
                order.getOrderId(),
                order.getOrderState(),
                order.getTotalAmount(),
                paymentPort.getBalance(memberId).balance(),
                order.getCanceledAt()
        );
    }

    // ── 조립 ────────────────────────────────────────────────────

    private ProductInfo findProduct(Long productId) {
        return productPort.findProduct(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    //본인 주문만. 없으면 ORDER_NOT_FOUND, 타인 주문이면 ACCESS_DENIED(403).
    private Order getOwnedOrder(Long memberId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return order;
    }

    private Order getOwnedOrderForUpdate(Long memberId, Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return order;
    }

    /**
     * 조회 대상 상태 목록.
     *
     * 필터를 주지 않으면 노출 가능한 상태 전부, 주면 그 하나만.
     * PENDING·FAILED·EXPIRED 를 필터로 넣으면 INVALID_ORDER_STATE 다 —
     * 정의된 값이더라도 이 화면에서 볼 수 있는 상태가 아니다.
     */
    private List<OrderState> resolveHistoryStates(String orderState) {
        if (orderState == null || orderState.isBlank()) {
            return Arrays.stream(OrderState.values())
                    .filter(OrderState::isVisibleInHistory)
                    .toList();
        }

        OrderState state;
        try {
            state = OrderState.valueOf(orderState);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATE);
        }
        if (!state.isVisibleInHistory()) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATE);
        }
        return List.of(state);
    }

    private OrderSheetItem toSheetItem(OrderItem item) {
        return new OrderSheetItem(
                item.getOrderItemId(),
                item.getProductNameSnapshot(),
                item.getImageUrlSnapshot(),
                item.getQuantity(),
                item.getUnitPriceSnapshot(),
                item.subtotal(),
                item.getPickUpDate()
        );
    }

    //목록 한 줄. 전부 스냅샷이라 건마다 상품·판매자를 다시 읽지 않는다.
    private OrderSummaryResult toSummary(Order order) {
        List<OrderItem> items = order.getItems();
        OrderItem representative = items.getFirst();

        return new OrderSummaryResult(
                order.getOrderId(),
                representative.getProductNameSnapshot(),
                representative.getImageUrlSnapshot(),
                items.size() - 1,
                representative.getSellerNameSnapshot(),
                items.stream().mapToInt(OrderItem::getQuantity).sum(),
                order.getTotalAmount(),
                order.getOrderState(),
                items.stream().map(OrderItem::getPickUpDate).min(Comparator.naturalOrder()).orElse(null),
                order.getPaidAt()
        );
    }

    private OrderDetailResult toDetail(Order order) {
        return new OrderDetailResult(
                order.getOrderId(),
                order.getOrderState(),
                order.getSalesType(),
                order.getItems().stream().map(this::toDetailItem).toList(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                order.getPaidAt(),
                order.getCanceledAt(),
                order.getReservationExpiresAt()
        );
    }

    private OrderDetailResult.OrderItemInfo toDetailItem(OrderItem item) {
        return new OrderDetailResult.OrderItemInfo(
                item.getOrderItemId(),
                item.getProductId(),
                item.getDropId(),
                item.getProductNameSnapshot(),
                item.getImageUrlSnapshot(),
                item.getUnitPriceSnapshot(),
                item.getQuantity(),
                item.subtotal(),
                item.getPickUpDate(),
                item.getItemStatus(),
                item.getConfirmedAt(),
                resolveSellerInfo(item)
        );
    }

    /**
     * 상세용 판매자 정보. 지도 보기·전화하기 버튼 때문에 주소·연락처를 포함한다.
     *
     * 상호명만 주문 시점 스냅샷을 쓰고 주소·연락처는 지금 값을 읽는다.
     * 옛 주소로 길을 안내하거나 옛 번호로 전화를 걸면 버튼이 하는 일 자체가 실패한다.
     */
    private OrderDetailResult.SellerInfo resolveSellerInfo(OrderItem item) {
        return sellerPort.findSeller(item.getSellerId())
                .map(seller -> new OrderDetailResult.SellerInfo(
                        item.getSellerId(),
                        item.getSellerNameSnapshot(),
                        seller.address(),
                        resolveSellerPhoneNumber(seller.memberId())))
                .orElse(new OrderDetailResult.SellerInfo(
                        item.getSellerId(), item.getSellerNameSnapshot(), null, null));
    }

    //판매자 연락처. 연결된 회원이 없거나 번호를 등록하지 않았으면 null.
    private String resolveSellerPhoneNumber(Long sellerMemberId) {
        if (sellerMemberId == null) {
            return null;
        }
        return memberPort.getMember(sellerMemberId).data().phoneNumber();
    }

    //판매자 화면. 자기 항목만 담고 금액도 자기 몫 소계다.
    private SellerOrderSummaryResult toSellerSummary(Order order, Long sellerId) {
        List<OrderItem> own = order.itemsOf(sellerId);

        return new SellerOrderSummaryResult(
                order.getOrderId(),
                order.getBuyerNameSnapshot(),
                order.getOrderState(),
                own.stream().map(OrderItem::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add),
                order.getPaidAt(),
                order.getCanceledAt(),
                own.stream()
                        .map(item -> new SellerOrderSummaryResult.SellerOrderItem(
                                item.getOrderItemId(),
                                item.getProductId(),
                                item.getDropId(),
                                item.getProductNameSnapshot(),
                                item.getQuantity(),
                                item.subtotal(),
                                item.getPickUpDate(),
                                item.getItemStatus(),
                                item.getConfirmedAt()))
                        .toList()
        );
    }
}
