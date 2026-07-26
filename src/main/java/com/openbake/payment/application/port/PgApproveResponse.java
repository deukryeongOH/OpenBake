package com.openbake.payment.application.port;

/**
 * PG 승인 응답 — 토스페이먼츠 승인 API의 응답에서 필요한 필드만 추출.
 * 실제 토스 응답에는 훨씬 많은 필드가 있지만, 우리가 쓰는 것만 매핑한다.
 */
public record PgApproveResponse(
        String paymentKey,
        String orderId,
        String method,      // 결제 수단 (카드, 계좌이체 등)
        String status        // DONE, CANCELED 등
) {
}
