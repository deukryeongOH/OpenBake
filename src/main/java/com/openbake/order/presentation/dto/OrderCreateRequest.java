package com.openbake.order.presentation.dto;

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

    @NotNull
    private Boolean termsAgreed;
}
