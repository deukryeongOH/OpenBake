package com.openbake.order.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 생성 요청. 주문 대상은 본문이 아니라 서버가 회원의 장바구니에서 읽는다.
 * 본문에는 약관 동의만 담긴다.
 */
@Getter
@NoArgsConstructor
public class OrderCreateRequest {

    @Schema(description = "약관 동의 여부. false 또는 누락이면 결제가 진행되지 않는다(OR004).", example = "true")
    @NotNull
    private Boolean termsAgreed;
}
