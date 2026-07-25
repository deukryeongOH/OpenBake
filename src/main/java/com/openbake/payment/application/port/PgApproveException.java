package com.openbake.payment.application.port;

import lombok.Getter;

/**
 * PG 승인 실패 시 던지는 예외.
 * 토스페이먼츠가 거절했을 때 실패 코드와 사유를 담는다.
 * (예: 카드 한도 초과, 카드 정지 등)
 */
@Getter
public class PgApproveException extends RuntimeException {

    private final String failureCode;
    private final String failureReason;

    public PgApproveException(String failureCode, String failureReason) {
        super("[PG 승인 실패] " + failureCode + ": " + failureReason);
        this.failureCode = failureCode;
        this.failureReason = failureReason;
    }
}
