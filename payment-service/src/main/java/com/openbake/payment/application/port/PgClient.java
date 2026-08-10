package com.openbake.payment.application.port;

import java.math.BigDecimal;

/**
 * PG사 API 호출 인터페이스.
 * 현재는 토스페이먼츠만 사용하지만, 인터페이스로 분리해서
 * 나중에 다른 PG사로 교체하거나 테스트용 Mock을 끼울 수 있게 한다.
 */
public interface PgClient {

    /**
     * PG 승인 요청.
     * 프론트에서 받은 paymentKey + orderId + amount로 PG에 최종 승인을 요청한다.
     * 승인이 실패하면 PgApproveException을 던진다.
     */
    PgApproveResponse approve(String pgPaymentKey, String pgOrderId, BigDecimal amount);

    /**
     * PG 결제 조회.
     * paymentKey로 토스페이먼츠에 결제 상태를 조회한다.
     * 미결 충전 확인 배치에서 IN_PROGRESS 상태의 실제 결과를 확인할 때 사용.
     */
    PgPaymentStatus getPaymentStatus(String pgPaymentKey);
}
