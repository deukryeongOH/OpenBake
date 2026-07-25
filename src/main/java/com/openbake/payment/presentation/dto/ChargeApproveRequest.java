package com.openbake.payment.presentation.dto;

import java.math.BigDecimal;

/**
 * 충전 승인 요청 — 프론트가 토스페이먼츠 결제창 완료 후 보내는 콜백 데이터.
 *
 * 토스페이먼츠 결제창이 완료되면 프론트에 paymentKey, orderId, amount가 전달되고,
 * 프론트가 이 값을 우리 서버에 보내면 서버가 PG 승인 API를 호출한다.
 *
 * 필드명은 API 명세(5-4)와 일치시킨다.
 */
public record ChargeApproveRequest(
        String paymentKey,    // 토스페이먼츠가 발급한 결제 키
        String orderId,       // 충전 요청 시 생성한 주문번호
        BigDecimal amount     // 결제 금액 (위변조 검증용)
) {
}
