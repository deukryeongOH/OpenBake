package com.openbake.order.application;

import com.openbake.cart.domain.Cart;
import com.openbake.cart.domain.CartItem;
import com.openbake.cart.domain.CartRepository;
import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.order.domain.Order;
import com.openbake.order.domain.OrderItem;
import com.openbake.order.domain.OrderRepository;
import com.openbake.order.presentation.dto.OrderCreateRequest;
import com.openbake.order.presentation.dto.OrderCreateResponse;
import com.openbake.payment.application.DepositService;
import com.openbake.payment.application.PaymentService;
import lombok.RequiredArgsConstructor;
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
}
