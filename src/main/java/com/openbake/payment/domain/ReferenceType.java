package com.openbake.payment.domain;

// WalletTransaction에서 "이 거래가 왜 생겼는지" 원인을 가리키는 유형 (다형성 참조)
public enum ReferenceType {
    CHARGE_REQUEST,  // 충전 요청으로 인한 거래
    ORDER_PAYMENT    // 주문 결제/환불로 인한 거래
}
