package com.openbake.payment.domain;

// DepositAccount에서 사용하는 계좌 유형
public enum AccountType {
    MEMBER,    // 회원 예치금 계좌 (회원당 1개, 잔액 추적)
    PLATFORM   // 판매대금 유입 원장 (시스템에 1개, 잔액 미추적).
               // 회원 예치금 → 판매대금 전환 흐름만 기록한다.
               // 판매자 정산 지급은 정산 도메인(Payout)이 관리하므로 여기에 남지 않는다.
}
