package com.openbake.order.domain;

/**
 * FAILED 로 끝난 이유. 상태를 더 늘리지 않고 사유만 남긴다.
 *
 * 사용자 안내 문구가 셋 다 다르기 때문에 구분한다.
 */
public enum OrderFailReason {
    //잔액 부족 등 payment FAIL 뒤 재결제하지 않고 만료되어 종료.
    PAYMENT_FAILED,
    //결제는 성공했는데 재고 차감이 실패해 환불로 되돌린 경우(7장).
    OUT_OF_STOCK,
    //타임아웃 이후 만료까지 결제 결과를 확정하지 못해 멱등 환불로 닫은 경우(8장).
    PAYMENT_UNKNOWN
}
