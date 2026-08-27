package com.openbake.order.application;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.order.application.port.CartPort;
import com.openbake.order.application.port.ProductPort;
import com.openbake.order.application.port.ReservationPort;
import com.openbake.order.application.port.dto.ProductInfo;
import com.openbake.order.domain.Order;
import com.openbake.order.domain.OrderFailReason;
import com.openbake.order.domain.OrderItem;
import com.openbake.order.domain.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * 결제 흐름의 DB 트랜잭션 조각들.
 *
 * <b>{@link OrderPayService} 에서 분리한 이유는 트랜잭션 경계 때문이다.</b>
 * 결제(Feign)는 order 트랜잭션 밖에서 커밋되므로, 외부 호출 전후를 각각 짧은 로컬
 * 트랜잭션으로 끊어야 한다. 같은 클래스 안의 메서드를 부르면 프록시를 타지 않아
 * @Transactional 이 걸리지 않으므로 빈을 나눴다.
 *
 * 네트워크 대기 중 DB 커넥션과 행 잠금을 붙잡지 않는 효과도 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPayTransactions {

    private final OrderRepository orderRepository;
    private final ProductPort productPort;
    private final CartPort cartPort;
    private final OrderStockRestorer stockRestorer;
    private final ReservationPort reservationPort;

    /**
     * T2 — 외부 결제 호출 <b>전에</b> 커밋되어야 하는 것들.
     *
     * payAttemptedAt 을 여기서 남기지 않고 결과 반영과 한 트랜잭션에 묶으면,
     * 타임아웃 시 order 트랜잭션이 롤백되면서 <b>결제를 시도했다는 사실까지 사라진다.</b>
     * 그러면 만료 배치가 "결제를 시도했으니 결과를 조회해야 한다"는 판단을 할 수 없다.
     */
    @Transactional
    public PayPreparation prepare(Long memberId, Long orderId, Boolean termsAgreed) {
        //약관 동의는 주문 생성이 아니라 결제 요청에 있다. 주문 생성 시점에는 아직 동의 전이다.
        if (termsAgreed == null || !termsAgreed) {
            throw new BusinessException(ErrorCode.TERMS_NOT_AGREED);
        }

        Order order = lockedOrder(orderId);
        validateOwner(order, memberId);
        if (order.getOrderState() != com.openbake.order.domain.OrderState.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATE);
        }
        if (order.isReservationExpired(LocalDateTime.now())) {
            //만료 배치가 정리하도록 두고 여기서는 막기만 한다. 결제부터 하지는 않는다.
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATE, "주문서가 만료되었습니다.");
        }

        validatePriceUnchanged(order);

        int attemptNo = order.preparePayAttempt();
        return new PayPreparation(order.getTotalAmount(), attemptNo, order.currentPaymentIdempotencyKey());
    }

    /**
     * 정상 pay 응답의 FAIL만 다음 사용자 요청을 새 시도로 표시한다.
     * 요청 당시 번호가 현재 번호와 다르면 늦게 도착한 이전 응답이므로 no-op이다.
     */
    @Transactional
    public boolean markPayFailed(Long orderId, int responseAttemptNo) {
        return lockedOrder(orderId).markPayFailed(responseAttemptNo);
    }

    /**
     * 가격 재검증 — 주문서에 표시한 금액과 지금 청구하려는 금액이 같은가.
     *
     * 2단계로 나누면서 주문서 표시와 청구 사이에 대기 구간이 생겼고, 그동안 판매자가
     * 가격을 바꿀 수 있다. <b>자동으로 재계산해서 결제하지 않는다</b> — 사용자가 확인한
     * 금액과 다른 금액을 동의 없이 청구하는 셈이 된다.
     *
     * 스냅샷도 갱신하지 않는다. 갱신하면 "사용자가 무슨 금액에 동의했는가"의 기록이 사라진다.
     * 주문은 PENDING 으로 남고, 새 가격에 동의하면 취소 후 새로 주문한다.
     *
     * <b>드롭은 검사하지 않는다.</b> 드롭 상품 수정은 DropService 가 validateEditable
     * (dropStatus == UPCOMING)로 막고, 주문은 lock-start(=ACTIVE) 이후에만 만들어진다.
     * 주문서를 쓰는 동안 가격이 바뀔 경로가 코드상 없어서 넣어도 항상 통과하는 검사가 된다.
     */
    private void validatePriceUnchanged(Order order) {
        if (order.isDrop()) {
            return;
        }

        StringJoiner changes = new StringJoiner(", ");
        for (OrderItem item : order.getItems()) {
            ProductInfo current = productPort.findProduct(item.getProductId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

            BigDecimal currentPrice = BigDecimal.valueOf(current.price());
            if (currentPrice.compareTo(item.getUnitPriceSnapshot()) != 0) {
                changes.add("%s %s원 → %s원".formatted(
                        item.getProductNameSnapshot(), item.getUnitPriceSnapshot(), currentPrice));
            }
        }

        if (changes.length() > 0) {
            //무엇이 얼마로 바뀌었는지 알려주지 않으면 사용자가 주문서를 갱신할 수 없다.
            throw new BusinessException(ErrorCode.PRICE_CHANGED, changes.toString());
        }
    }

    /**
     * T4 성공 경로 — 재고 차감 + PAID + 장바구니 정리.
     *
     * <b>재고 차감이 결제 뒤로 밀렸으므로 여기서 실패할 수 있다.</b> 실패하면 예외로
     * 트랜잭션을 통째로 롤백해 앞서 차감한 항목들도 함께 되돌린다 —
     * 손으로 되감을 필요가 없다.
     *
     * 드롭은 lock-start 에서 이미 깎여 있어 차감하지 않는다.
     *
     * ⚠️ <b>재고 차감 뒤에는 주문을 다시 읽는다.</b> ProductInventory 의 차감·복구는
     * {@code @Modifying(clearAutomatically = true)} 벌크 UPDATE 라 실행 즉시 영속성
     * 컨텍스트를 비운다. 그 앞에서 읽어 둔 Order 는 그 순간 준영속이 되어,
     * 이후 markPaid() 를 호출해도 <b>더티 체킹이 돌지 않아 UPDATE 가 나가지 않는다</b>
     * (예외도 로그도 없이 조용히 유실된다). 그래서 차감을 먼저 끝내고 다시 읽는다.
     *
     * @throws BusinessException OUT_OF_STOCK — 호출한 쪽이 이걸 받아 환불로 되돌린다
     */
    @Transactional
    public PaymentApplyResult decreaseStockAndMarkPaid(Long orderId) {
        Order order = lockedOrder(orderId);

        //같은 결제 결과가 재전송된 경우 재고를 다시 깎지 않고 기존 성공을 재생한다.
        if (order.getOrderState() == com.openbake.order.domain.OrderState.PAID) {
            return PaymentApplyResult.ALREADY_PAID;
        }

        //결제 원격 호출 중 사용자가 취소했거나 만료 배치가 먼저 닫은 경우다.
        //호출한 서비스가 이미 성공한 결제를 환불해야 한다.
        if (order.getOrderState() != com.openbake.order.domain.OrderState.PENDING) {
            return PaymentApplyResult.ORDER_ALREADY_CLOSED;
        }

        //차감에 쓸 값을 먼저 뽑아 둔다. 첫 차감이 컨텍스트를 비우면 order 의 지연 로딩이 끊긴다.
        List<StockLine> stockLines = order.isDrop() ? List.of() : stockLinesOf(order);

        for (StockLine line : stockLines) {
            if (!productPort.decreaseStock(line.productId(), line.quantity())) {
                log.warn("결제 성공 후 재고 차감 실패 — 환불로 되돌린다. orderId={}, productId={}",
                        orderId, line.productId());
                throw new BusinessException(ErrorCode.OUT_OF_STOCK);
            }
        }

        //드롭은 lock-start 에서 이미 깎여 있으므로 차감할 것은 없고, 선점을 확정만 한다
        //(drop_entry RESERVED -> COMPLETED). docs/10 3.1절 — 이걸 해야 방치된 선점을
        //회수하는 만료 스위퍼(2단계, 아직 미착수)가 결제 완료 건까지 회수하지 않는다.
        if (order.isDrop()) {
            for (OrderItem item : order.getItems()) {
                reservationPort.complete(item.getDropId(), order.getMemberId());
            }
        }

        //벌크 UPDATE 로 비워진 컨텍스트에 다시 올린다. 같은 트랜잭션이라 행 잠금은 유지된다.
        Order managed = lockedOrder(orderId);
        managed.markPaid();
        removeOrderedCartItems(managed);
        return PaymentApplyResult.PAID;
    }

    private List<StockLine> stockLinesOf(Order order) {
        return order.getItems().stream()
                .map(item -> new StockLine(item.getProductId(), item.getQuantity()))
                .toList();
    }

    private record StockLine(Long productId, int quantity) {
    }

    /**
     * 주문한 장바구니 항목만 지운다. 바로 주문·드롭은 지울 것이 없다.
     *
     * removeItems 는 멱등이라 이미 지워진 id 를 넘겨도 안전하다.
     * 타임아웃 후 뒤늦게 PAID 를 확정하는 재처리에서도 같은 값으로 부를 수 있다.
     */
    private void removeOrderedCartItems(Order order) {
        List<Long> cartItemIds = order.getItems().stream()
                .map(OrderItem::getSourceCartItemId)
                .filter(Objects::nonNull)
                .toList();

        if (!cartItemIds.isEmpty()) {
            cartPort.removeItems(order.getMemberId(), cartItemIds);
        }
    }

    /**
     * T4 실패 경로 — 재고 복구 + FAILED.
     *
     * 일반 상품은 아직 차감 전이라 복구할 것이 없고, 드롭만 선점을 되돌린다.
     *
     * ⚠️ 드롭 복구는 재고 벌크 UPDATE 를 타고 영속성 컨텍스트를 비운다.
     * 복구 뒤에 주문을 다시 읽어야 상태 전이가 저장된다(decreaseStockAndMarkPaid 참고).
     */
    @Transactional
    public void markFailed(Long orderId, OrderFailReason reason) {
        Order order = lockedOrder(orderId);
        if (order.getOrderState() != com.openbake.order.domain.OrderState.PENDING) {
            return;
        }
        stockRestorer.restorePending(order);
        lockedOrder(orderId).markFailed(reason);
    }

    //환불까지 끝난 뒤 상태만 닫는다. 재고는 애초에 차감되지 않았거나 이미 롤백됐다.
    @Transactional
    public void markFailedWithoutRestore(Long orderId, OrderFailReason reason) {
        Order order = lockedOrder(orderId);
        if (order.getOrderState() == com.openbake.order.domain.OrderState.PENDING) {
            order.markFailed(reason);
        }
    }

    @Transactional(readOnly = true)
    public Order load(Long orderId) {
        return order(orderId);
    }

    private Order lockedOrder(Long orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }

    private Order order(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }

    private void validateOwner(Order order, Long memberId) {
        if (!order.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    //외부 결제 호출에 사용할 값. 금액과 키는 주문 DB 트랜잭션에서 함께 확정한다.
    public record PayPreparation(BigDecimal amount, int attemptNo, String idempotencyKey) {
    }

    public enum PaymentApplyResult {
        PAID,
        ALREADY_PAID,
        ORDER_ALREADY_CLOSED
    }
}
