package com.openbake.order.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 결제 요청.
 *
 * 약관 동의가 주문 생성이 아니라 여기 있는 이유는, 주문 생성 시점에는 사용자가
 * 아직 주문서를 보지도 않았기 때문이다. 동의는 결제 버튼 앞에서 받는다.
 */
public record OrderPayRequest(

        @Schema(description = "약관 동의 여부. false 또는 누락이면 결제가 진행되지 않는다(OR004).", example = "true")
        @NotNull
        Boolean termsAgreed
) {
}
