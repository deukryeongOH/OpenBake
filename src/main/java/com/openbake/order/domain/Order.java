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
 * 주문.
 * 재고는 장바구니 생성 시점에 이미 선점됐으므로 주문은 재고를 건드리지 않는다.
 * 상태 전이: PAID → CONFIRMED (구매확정) 또는 PAID → CANCELED (취소).
 * 전이 메서드는 PAID 가 아니면 예외를 던져 중복 취소·확정을 막는다.
 */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long orderId;

    @Column(nullable = false)
    private Long memberId;

    //판매자 비정규화 — 드롭을 거치지 않고 판매자를 특정하기 위함(구매확정 권한 판정, 정산).
    @Column(nullable = false)
    private Long sellerId;

    @Column(nullable = false)
    private LocalDate pickupDate;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderState orderState;

    //주문 1건당 항목 1개(장바구니가 단건이므로). Cart ↔ CartItem 과 동일한 구조.
    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private OrderItem orderItem;

    @Column(nullable = false)
    private LocalDateTime paidAt;

    private LocalDateTime confirmAt;
    private LocalDateTime cancelAt;

    //정적 팩토리. 생성 시 결제완료(PAID) 상태로 시작한다.
    public static Order create(Long memberId, Long sellerId, LocalDate pickupDate, BigDecimal totalAmount) {
        Order order = new Order();
        order.memberId = memberId;
        order.sellerId = sellerId;
        order.pickupDate = pickupDate;
        order.totalAmount = totalAmount;
        order.orderState = OrderState.PAID;
        order.paidAt = LocalDateTime.now();
        return order;
    }

    //항목 연결. 주문 1건에 항목은 하나만 담긴다.
    public void addItem(OrderItem item) {
        if (this.orderItem != null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "주문에는 하나의 항목만 담을 수 있습니다.");
        }
        this.orderItem = item;
        item.setOrder(this);
    }

    //구매확정 — PAID 일 때만. 이미 CONFIRMED/CANCELED 면 예외(배치 자동확정과 수동확정 충돌 방어).
    public void confirm() {
        if (this.orderState != OrderState.PAID) {
            throw new BusinessException(ErrorCode.ORDER_NOT_CONFIRMABLE);
        }
        this.orderState = OrderState.CONFIRMED;
        this.confirmAt = LocalDateTime.now();
    }

    //취소 — PAID 일 때만. 이미 CONFIRMED/CANCELED 면 예외(중복 취소 방어).
    public void cancel() {
        if (this.orderState != OrderState.PAID) {
            throw new BusinessException(ErrorCode.ORDER_NOT_CANCELABLE);
        }
        this.orderState = OrderState.CANCELED;
        this.cancelAt = LocalDateTime.now();
    }


    //취소 가능 여부. 드롭 마감 시각은 도메인이 조회할 수 없어 밖에서 주입받는다.
    public boolean isCancelable(LocalDateTime now, LocalDateTime dropCloseAt) {
        return this.orderState == OrderState.PAID && now.isBefore(dropCloseAt);
    }
}
