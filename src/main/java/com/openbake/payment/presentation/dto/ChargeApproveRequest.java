package com.openbake.payment.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

public record ChargeApproveRequest(
        @Schema(description = "토스페이먼츠가 발급한 결제 키", example = "tviva20260717140212ABCD1")
        String paymentKey,
        @Schema(description = "충전 요청 시 생성한 주문번호 (pgOrderId)", example = "a3f1c2e8-9b4d-4c1a-8e5f-2d7b1c3a4e6f")
        String orderId,
        @Schema(description = "결제 금액 (위변조 검증용)", example = "50000")
        BigDecimal amount
) {
}
