package com.openbake.order.domain;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 주문 항목.
 *
 * 주문 시점의 가격·상품명·판매자를 스냅샷으로 복사해 둔다. 상품이나 판매자를 조인하지 않으므로
 * 나중에 가격·상호명이 바뀌거나 상품이 삭제돼도 과거 주문 내역은 당시 값을 유지한다.
 *
 * 판매자와 픽업일이 항목에 있는 이유는 <b>한 주문에 판매자가 여럿</b>일 수 있기 때문이다.
 * 판매자가 다르면 픽업일도 다를 수밖에 없다.
 *
 * 구매확정도 항목 단위다 — 확정은 "이 손님이 내 빵을 가져갔다"는 판매자의 확인이라
 * 판매자 A 가 B 의 항목까지 확정할 수는 없다.
 */
@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long orderItemId;

    //주문과 N:1. 연관관계 주인은 이쪽(order_id FK 를 가진다).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /**
     * 이 항목이 어느 장바구니 항목에서 왔는가. 장바구니 경로에서만 채워진다.
     *
     * 결제가 성공한 뒤 주문한 항목만 장바구니에서 지우는 근거다. 타임아웃 이후 뒤늦게
     * PAID 가 확정되는 재처리에서도 같은 값으로 removeItems 를 호출할 수 있다.
     * 바로 주문·드롭 주문은 null.
     */
    @Column(name = "source_cart_item_id")
    private Long sourceCartItemId;

    /**
     * 드롭 주문에서만 채운다.
     *
     * 정산으로는 보내지 않는다(정산은 productId 로 받는다). 회차별 정산이 나중에
     * 요구사항이 되면 orderItemId 로 조인해 백필할 근거로 남겨 둔다.
     */
    @Column(name = "drop_id")
    private Long dropId;

    //드롭·일반 양쪽 모두 채운다. 일반 상품 주문의 키이자 정산으로 보내는 값이다.
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    //단가 스냅샷. 결제 직전 재검증(OR010)의 대조 기준이기도 하다.
    @Column(name = "unit_price_snapshot", nullable = false)
    private BigDecimal unitPriceSnapshot;

    @Column(name = "product_name_snapshot", nullable = false)
    private String productNameSnapshot;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    /**
     * 판매자 상호명 스냅샷. 상호가 바뀌어도 주문 당시 이름이 내역에 남아야 한다.
     *
     * 주소·연락처는 스냅샷하지 않는다. 지도 보기·전화 걸기에 쓰이는 값이라
     * 옛 값이 남으면 버튼이 하는 일 자체가 실패한다. 조회 시점의 최신값을 쓴다.
     */
    @Column(name = "seller_name_snapshot")
    private String sellerNameSnapshot;

    @Column(name = "pick_up_date", nullable = false)
    private LocalDate pickUpDate;

    @Column(name = "image_url_snapshot", columnDefinition = "TEXT")
    private String imageUrlSnapshot;

    //구매확정은 주문 전체가 아니라 항목별 상태다.
    @Enumerated(EnumType.STRING)
    @Column(name = "item_status", nullable = false, length = 20)
    private OrderItemStatus itemStatus;

    //판매자가 이 항목을 확정한 시각. 다른 항목이나 Order 상태에는 영향을 주지 않는다.
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    //정적 팩토리. order 연결은 Order.addItem 에서 이뤄진다.
    public static OrderItem create(
            Long productId,
            Long dropId,
            Long sourceCartItemId,
            int quantity,
            BigDecimal unitPriceSnapshot,
            String productNameSnapshot,
            Long sellerId,
            String sellerNameSnapshot,
            LocalDate pickUpDate,
            String imageUrlSnapshot) {
        OrderItem item = new OrderItem();
        item.productId = productId;
        item.dropId = dropId;
        item.sourceCartItemId = sourceCartItemId;
        item.quantity = quantity;
        item.unitPriceSnapshot = unitPriceSnapshot;
        item.productNameSnapshot = productNameSnapshot;
        item.sellerId = sellerId;
        item.sellerNameSnapshot = sellerNameSnapshot;
        item.pickUpDate = pickUpDate;
        item.imageUrlSnapshot = imageUrlSnapshot;
        item.itemStatus = OrderItemStatus.UNCONFIRMED;
        return item;
    }

    /**
     * 항목 소계. 정산으로 보내는 grossAmount 가 이 값이다.
     *
     * 주문 전체 합계를 보내면 한 주문에 판매자가 둘일 때 판매자마다 전체 금액이 정산돼
     * 받은 돈보다 많은 금액이 지급된다.
     */
    public BigDecimal subtotal() {
        return unitPriceSnapshot.multiply(BigDecimal.valueOf(quantity));
    }

    public boolean isConfirmed() {
        return itemStatus == OrderItemStatus.CONFIRMED;
    }

    //항목 확정. 이미 확정된 항목이면 예외(수동 확정과 자동 확정 배치의 충돌 방어).
    void confirm() {
        if (itemStatus != OrderItemStatus.UNCONFIRMED) {
            throw new BusinessException(ErrorCode.ORDER_NOT_CONFIRMABLE);
        }
        this.itemStatus = OrderItemStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    /**
     * 결제 후 주문 전체 취소에 따른 항목 취소.
     *
     * <p>이미 구매확정된 항목은 정산 대상이므로 취소할 수 없다. 현재는 부분 취소 API가
     * 없지만 상태를 항목에 두어 이후 항목별 취소를 추가해도 Order의 구매확정 상태에
     * 의존하지 않게 한다.</p>
     */
    void cancel() {
        if (itemStatus != OrderItemStatus.UNCONFIRMED) {
            throw new BusinessException(ErrorCode.ORDER_NOT_CANCELABLE);
        }
        this.itemStatus = OrderItemStatus.CANCELED;
    }

    //연관관계 편의 메서드. Order.addItem 에서만 호출한다.
    void setOrder(Order order) {
        this.order = order;
    }
}
