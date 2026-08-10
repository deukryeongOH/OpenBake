package com.openbake.payment.domain;

// WalletTransaction(거래 내역 원장)에서 사용하는 거래 유형
public enum TransactionType {
    CHARGE,   // PG 충전으로 예치금 증가
    PAYMENT,  // 주문 시 예치금 차감
    REFUND    // 주문 취소 시 예치금 복구
}
