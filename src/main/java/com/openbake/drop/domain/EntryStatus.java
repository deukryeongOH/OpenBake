package com.openbake.drop.domain;

import lombok.Getter;

@Getter
public enum EntryStatus {
    ENTERED("입장"), // 드롭 상세 페이지 진입 성공 (대기열 통과)
    RESERVED("재고 선점 성공"), // 수량 선택 후 [주문하기] 클릭 -> 재고 선점 성공
    COMPLETED("드롭 참여 완료"), // 결제까지 완료 (구매 성공 - 절대 재진입 X)
    FAILED("실패"), // 재고 소진 / 타임아웃 등으로 인한 응모 실패
    CANCELLED("취소"); // 유저가 주문을 취소하여 재고가 복구된 상태

    private final String message;

    EntryStatus(String message) {
        this.message = message;
    }
}
