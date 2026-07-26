package com.openbake.payment.application.port;

import lombok.Getter;

/**
 * PG 결과를 알 수 없을 때 던지는 예외.
 *
 * "모름"에 해당하는 모든 상황:
 * - 타임아웃 (connect/read)
 * - 네트워크 단절
 * - PG 서버 오류 (5xx)
 * - 응답 파싱 실패
 * - 이미 처리된 결제 (ALREADY_PROCESSED 등)
 *
 * 이 예외가 던져지면 ChargeFacade는 failCharge()를 호출하지 않는다.
 * IN_PROGRESS 상태가 유지되고, 배치가 나중에 PG에 조회해서 실제 결과를 반영한다.
 */
@Getter
public class PgUnknownResultException extends RuntimeException {

    private final String reason;

    public PgUnknownResultException(String reason) {
        super("[PG 결과 모름] " + reason);
        this.reason = reason;
    }

    public PgUnknownResultException(String reason, Throwable cause) {
        super("[PG 결과 모름] " + reason, cause);
        this.reason = reason;
    }
}
