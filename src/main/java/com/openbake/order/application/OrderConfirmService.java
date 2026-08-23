package com.openbake.order.application;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.order.application.port.PaymentPort;
import com.openbake.order.application.port.SellerPort;
import com.openbake.order.application.port.SettlementPort;
import com.openbake.order.application.port.dto.PaymentResult;
import com.openbake.order.application.port.dto.PurchaseConfirmedInfo;
import com.openbake.order.domain.Order;
import com.openbake.order.domain.OrderItem;
import com.openbake.order.domain.OrderItemStatus;
import com.openbake.order.domain.OrderRepository;
import com.openbake.order.domain.OrderState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * 구매확정.
 *
 * <b>확정만 항목 단위다.</b> 진행 상태(PENDING→PAID→…)는 주문 전체가 한 덩어리로
 * 움직이지만, 확정은 "이 손님이 내 빵을 가져갔다"는 판매자의 확인이라
 * 판매자 A 가 B 의 항목까지 확정할 수는 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderConfirmService {

    private final OrderRepository orderRepository;
    private final SellerPort sellerPort;
    private final PaymentPort paymentPort;
    private final SettlementPort settlementPort;
    private final PurchaseConfirmedOutboxWriter purchaseConfirmedOutboxWriter;

    //자동 구매확정 기준 일수. 결제 완료 후 이 일수가 지나면 자동 확정한다(정책값).
    @Value("${openbake.order.auto-confirm-days:1}")
    private long autoConfirmDays;

    /**
     * 판매자 수동 확정. <b>자기 sellerId 항목만</b> 확정할 수 있다.
     *
     * member 에 SELLER role 이 없어 role 로는 판정할 수 없다.
     * 판매자 판정은 sellers.member_id + 승인 상태로 하고(CurrentSellerProvider),
     * 그 sellerId 와 항목의 sellerId 를 대조한다.
     */
    @Transactional
    public OrderConfirmResult confirmItem(Long orderItemId) {
        Long sellerId = sellerPort.getCurrentSellerId()
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCESS_DENIED));

        Order order = orderRepository.findByItemIdForUpdate(orderItemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        OrderItem item = order.getItems().stream()
                .filter(i -> i.getOrderItemId().equals(orderItemId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        //판매자로 등록돼 있다고 아무 항목이나 확정할 수 있는 것이 아니다.
        if (!item.getSellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        confirmAndPublish(order, item);

        return new OrderConfirmResult(
                order.getOrderId(),
                orderItemId,
                item.getItemStatus(),
                item.getConfirmedAt()
        );
    }

    /**
     * 자동 확정 배치. 판매자가 확정을 누르지 않아도 정산이 누락되지 않게 하는 안전망이다.
     *
     * 아직 확정되지 않은 항목만 확정하고, 이미 확정된 항목은 건너뛴다.
     */
    @Transactional
    public void autoConfirmItem(Long orderItemId) {
        Order order = orderRepository.findByItemIdForUpdate(orderItemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        //조회 후 확정 사이에 판매자가 수동 확정하거나 주문 전체가 취소됐을 수 있다(멱등).
        if (order.getOrderState() != OrderState.PAID) {
            return;
        }

        OrderItem item = order.getItems().stream()
                .filter(candidate -> candidate.getOrderItemId().equals(orderItemId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (item.getItemStatus() != OrderItemStatus.UNCONFIRMED) {
            return;
        }

        confirmAndPublish(order, item);
    }

    @Transactional(readOnly = true)
    public List<Long> findAutoConfirmTargetItemIds() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(autoConfirmDays);
        return orderRepository.findAutoConfirmTargetItemIds(cutoff);
    }

    /**
     * 항목 확정 + 정산 이벤트 발행. 수동·자동이 공유한다.
     *
     * <b>paymentPort.confirm(orderId) 은 마지막 미확정 항목에서만 부른다.</b>
     * OrderPayment 는 주문당 1행(orderId UNIQUE)이고 confirm() 이 PAID 가 아니면
     * INVALID_PAYMENT_STATUS 를 던지므로, 항목마다 부르면 두 번째 항목에서 터진다.
     *
     * 반면 정산 이벤트는 <b>항목마다</b> 발행한다. 정산이 UNIQUE(order_item_id) 로
     * 항목 단위를 전제하고 있어 그쪽 구조와 그대로 맞는다.
     *
     * Payment의 주문 단위 최종화는 유지하지만 Order 상태는 PAID에서 바꾸지 않는다.
     */
    private void confirmAndPublish(Order order, OrderItem item) {
        boolean allConfirmed = order.confirmItem(item);

        if (allConfirmed) {
            PaymentResult result = paymentPort.confirm(order.getOrderId());
            if (!result.isSuccess()) {
                throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
            }
        }

        publishPurchaseConfirmed(order, item);
        writeInteractionOutbox(order, item);
    }

    /**
     * 사용자 행동 이벤트(PURCHASE)를 Outbox 에 남긴다. 추천·분석이 소비한다.
     *
     * 주문이 드롭 전용이던 시절에는 {@code write(memberId, dropId, ...)} 로 불러
     * drop 에서 productId 를 되찾았다. 리팩터링으로 {@link OrderItem} 이 productId 를
     * 직접 갖게 되어 그 조회가 필요 없고, <b>dropId 가 없는 일반 상품도 이벤트가 남는다.</b>
     */
    private void writeInteractionOutbox(Order order, OrderItem item) {
        purchaseConfirmedOutboxWriter.write(
                order.getMemberId(),
                item.getProductId(),
                item.getDropId(),
                item.getQuantity(),
                order.getOrderId(),
                item.getConfirmedAt().atZone(ZoneId.systemDefault()).toInstant());
    }

    /**
     * 구매확정 이벤트를 정산으로 발행한다.
     *
     * 확정 트랜잭션 안에서 발행하지만 정산 리스너가 AFTER_COMMIT 이라,
     * 확정이 롤백되면 정산은 실행되지 않고 커밋된 뒤에야 별도 트랜잭션에서 처리된다.
     *
     * <b>grossAmount 는 항목 소계다.</b> 주문 전체 합계를 보내면 한 주문에 판매자가
     * 둘일 때 판매자마다 전체 금액이 정산돼 받은 돈보다 많은 금액이 지급된다.
     */
    private void publishPurchaseConfirmed(Order order, OrderItem item) {
        settlementPort.publishPurchaseConfirmed(new PurchaseConfirmedInfo(
                //정산이 중복 수신을 걸러내는 멱등 키.
                UUID.randomUUID().toString(),
                order.getOrderId(),
                item.getOrderItemId(),
                item.getSellerId(),
                //dropId 가 아니다. 일반 상품에는 dropId 가 없고 정산은 회차를 구분하지 않는다.
                item.getProductId(),
                item.getProductNameSnapshot(),
                item.getQuantity(),
                item.subtotal(),
                //LocalDateTime → 정산이 요구하는 OffsetDateTime 으로 변환(시스템 존 기준).
                item.getConfirmedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime()
        ));
    }
}
