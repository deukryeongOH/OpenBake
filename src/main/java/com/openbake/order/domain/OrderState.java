package com.openbake.order.domain;

/**
 * 주문 상태.
 *
 * <pre>
 * PENDING ──결제 성공──→ PAID ──전체 취소──→ CANCELED
 *    ├──결제 FAIL──→ PENDING(충전 후 재결제)
 *    ├──결제 시도 후 만료──→ FAILED
 *    └──결제 미시도 만료──→ EXPIRED
 * </pre>
 *
 * 구매확정은 Order 상태가 아니라 OrderItem 상태다. Order는 여러 상품을 묶은 주문서와
 * 결제 생명주기만 표현한다. 주문 내역 조회는 PAID / CANCELED 만 내려준다.
 * PENDING 은 진행 중이라 별도 화면이고, FAILED·EXPIRED 는 사용자 입장에서
 * "주문한 적이 없는" 것이므로 노출하지 않는다.
 */
public enum OrderState {
    //주문 생성됨, 결제 전. Saga 앵커이자 orderId(=멱등키 재료) 확보 지점.
    PENDING,
    PAID,
    //결제 후 취소 — 환불 + 재고 복구. 주문 내역에 노출한다.
    CANCELED,
    //결제 시도 만료 또는 결제 후 재고 부족 보상이 끝나 실패로 종료. failReason으로 구분한다.
    FAILED,
    //결제 미시도 만료, 결제 전 사용자 취소, 드롭 우선권으로 종료. 노출하지 않는다.
    EXPIRED;

    //주문 내역 조회 대상인가.
    public boolean isVisibleInHistory() {
        return this == PAID || this == CANCELED;
    }

    /**
     * 회원의 진행 중 주문 슬롯을 반납한 상태인가.
     *
     * 이름이 terminal 이지만 상태가 영원히 불변이라는 뜻은 아니다.
     * PAID도 슬롯은 반납하므로 true이고, 이후 전체 취소 시 CANCELED로 바뀔 수 있다.
     */
    public boolean isTerminal() {
        return this != PENDING;
    }
}
