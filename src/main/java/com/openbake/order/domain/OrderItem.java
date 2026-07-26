package com.openbake.order.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 주문 항목.
 * 주문 시점의 가격·상품명을 스냅샷으로 복사해 둔다. 드롭을 조인하지 않으므로
 * 판매자가 나중에 가격·상품명을 바꿔도 과거 주문 내역은 당시 값을 유지한다.
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

    //주문과 1:1. 연관관계 주인은 이쪽(order_id FK 를 가진다).
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private Long dropId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private BigDecimal priceSnapshot;

    @Column(nullable = false)
    private String dropNameSnapshot;

    //정적 팩토리. order 연결은 Order.addItem 에서 이뤄진다.
    public static OrderItem create(Long dropId, int quantity, BigDecimal priceSnapshot, String dropNameSnapshot) {
        OrderItem item = new OrderItem();
        item.dropId = dropId;
        item.quantity = quantity;
        item.priceSnapshot = priceSnapshot;
        item.dropNameSnapshot = dropNameSnapshot;
        return item;
    }

    //연관관계 편의 메서드. Order.addItem 에서만 호출한다.
    void setOrder(Order order) {
        this.order = order;
    }
}
