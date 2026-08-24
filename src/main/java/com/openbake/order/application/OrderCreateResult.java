package com.openbake.order.application;

import com.openbake.order.domain.OrderState;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 생성 결과. 아직 결제 전(PENDING)이라 결제 관련 값이 없다.
 *
 * reservationExpiresAt 을 내려주는 이유는 주문서 화면이 남은 시간을 표시해야 하기 때문이다.
 */
public record OrderCreateResult(
        Long orderId,
        OrderState orderState,
        BigDecimal totalAmount,
        LocalDateTime reservationExpiresAt,
        List<OrderSheetItem> items,
        //드롭 우선권으로 기존 진행 중 주문을 만료시켰다면 그 주문 ID. 아니면 null.
        Long yieldedOrderId
) {
}
