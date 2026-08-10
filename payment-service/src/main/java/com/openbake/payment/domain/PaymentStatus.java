package com.openbake.payment.domain;

// OrderPayment에서 사용하는 주문 결제 상태
public enum PaymentStatus {
    PAID,       // 결제 완료 (예치금 차감됨)
    CONFIRMED,  // 구매 확정 (정산 대상)
    REFUNDED    // 환불 완료 (예치금 복구됨)
}
