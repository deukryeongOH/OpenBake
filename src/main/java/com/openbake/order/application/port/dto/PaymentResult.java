package com.openbake.order.application.port.dto;

public record PaymentResult(String status, String message) {

    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }

    //잔액 부족 등 업무 실패. payment 는 이걸 예외가 아니라 200 + status=FAIL 로 준다.
    public boolean isFail() {
        return "FAIL".equals(status);
    }

    /**
     * 조회 시점에 확정 기록이 없음. <b>실패가 아니다.</b>
     *
     * 원래 결제 트랜잭션이 아직 커밋 전이면 기록이 보이지 않을 수 있다.
     * 그래서 NOT_FOUND 를 받아도 만료 전까지는 PENDING 을 유지하고 다시 조회한다.
     */
    public boolean isNotFound() {
        return "NOT_FOUND".equals(status);
    }
}
