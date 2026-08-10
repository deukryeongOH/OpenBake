package com.openbake.payment.domain;

// ChargeRequest에서 사용하는 PG 충전 상태
public enum ChargeStatus {
    READY,        // 충전 요청 생성됨, PG 결제창 대기 중
    IN_PROGRESS,  // PG에 승인 요청 보냄, 결과 대기 중
    DONE,         // 충전 완료, 예치금 증가됨
    FAILED,       // PG 승인 거절 (한도 초과, 카드 정지 등)
    EXPIRED       // 30분 안에 완료 안 됨
}
