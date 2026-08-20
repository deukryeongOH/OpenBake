package com.openbake.settlement.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 구매확정 이벤트를 정산 도메인에 전달하기 위한 커맨드입니다.
 * (구매확정 정보를 받아서 정산 처리를 실행해줘
 * :구매확정 정보를 받아 정산 대상을 생성하는 상태 변경 작업을 서비스에 전달하는 객체)
 *
 * Presentation 계층에서 HTTP 요청을 받은 뒤
 * Application 계층으로 데이터를 전달할 때 사용합니다.
 */
public record ReceivePurchaseConfirmedCommand(

        /**
         * 이벤트를 구분하는 고유 ID입니다.
         *
         * 같은 이벤트가 여러 번 전달될 경우
         * 중복 처리를 방지하는 기준으로 사용합니다.
         */
        String eventId,

        /**
         * 구매가 확정된 주문 ID입니다.
         */
        Long orderId,

        /**
         * 구매가 확정된 주문 상품 ID입니다.
         */
        Long orderItemId,

        /**
         * 상품 판매자 ID입니다.
         */
        Long sellerId,
        Long productId,
        /**
         * 구매확정 당시의 상품명입니다.
         */
        String productNameSnapshot,

        /**
         * 구매 수량입니다.
         */
        Integer quantity,

        /**
         * 수수료를 차감하기 전 주문 상품의 총 판매 금액입니다.
         */
        BigDecimal grossAmount,
        /**
         * 구매가 확정된 시각입니다.
         */
        OffsetDateTime purchaseConfirmedAt
) {
}