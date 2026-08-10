package com.openbake.payment.application.port;

/**
 * PG 결제 조회 응답 — 토스페이먼츠 결제 조회 API에서 필요한 필드만 추출.
 * 미결 충전 확인 배치에서 IN_PROGRESS 건의 실제 상태를 판단하는 데 사용.
 */
public record PgPaymentStatus(
        String paymentKey,
        String orderId,
        String status,   // DONE, CANCELED, ABORTED, EXPIRED 등
        String method     // 결제 수단 (카드, 계좌이체 등)
) {

    public boolean isDone() {
        return "DONE".equals(status);
    }

    public boolean isFailed() {
        return "CANCELED".equals(status)
                || "ABORTED".equals(status)
                || "EXPIRED".equals(status);
    }
}
