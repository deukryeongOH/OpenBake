package com.openbake.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 주문 결제.
 * 주문 1건당 1개. 주문 시 PAID로 생성되고, 이후 확정(CONFIRMED) 또는 환불(REFUNDED)로 전이된다.
 *
 * 상태 전이: PAID → CONFIRMED (구매확정, 정산 대상)
 *           PAID → REFUNDED  (환불, 예치금 복구)
 */
@Entity
@Table(name = "order_payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long orderId;  // 주문 도메인의 Order ID (FK 아님, 애플리케이션 레벨 참조)

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private BigDecimal amount;  // 결제 금액

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false)
    private LocalDateTime paidAt;

    private LocalDateTime confirmedAt;

    private LocalDateTime refundedAt;

    // 생성 시 PAID 상태로 시작
    public static OrderPayment create(Long orderId, Long memberId, BigDecimal amount) {
        OrderPayment payment = new OrderPayment();
        payment.orderId = orderId;
        payment.memberId = memberId;
        payment.amount = amount;
        payment.status = PaymentStatus.PAID;
        payment.paidAt = LocalDateTime.now();
        return payment;
    }

    // 구매 확정 — PAID일 때만 가능
    public void confirm() {
        validateStatus(PaymentStatus.PAID);
        this.status = PaymentStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    // 환불 — PAID일 때만 가능
    public void refund() {
        validateStatus(PaymentStatus.PAID);
        this.status = PaymentStatus.REFUNDED;
        this.refundedAt = LocalDateTime.now();
    }

    public boolean isRefundable() {
        return this.status == PaymentStatus.PAID;
    }

    // 현재 상태가 expected가 아니면 예외
    private void validateStatus(PaymentStatus expected) {
        if (this.status != expected) {
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_STATUS,
                    "처리할 수 없는 결제 상태입니다: " + this.status);
        }
    }
}
