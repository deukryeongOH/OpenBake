package com.openbake.order.application;

import com.openbake.order.domain.OrderState;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 주문 목록 한 줄.
 *
 * 항목이 여럿이 되면서 "무엇을 샀는지"를 한 줄로 줄여야 한다 —
 * 대표 상품명 + 나머지 건수로 표시한다("소금빵 외 2건").
 * 값은 전부 주문 시점 스냅샷이라 건마다 상품·판매자를 다시 읽지 않는다.
 */
public record OrderSummaryResult(
        Long orderId,
        String representativeProductName,
        //대표 상품을 뺀 나머지 항목 수. 0 이면 단일 항목 주문이다.
        int otherItemCount,
        String representativeSellerName,
        int totalQuantity,
        BigDecimal totalAmount,
        OrderState orderState,
        //항목마다 픽업일이 다를 수 있어 가장 이른 날짜를 대표로 쓴다.
        LocalDate nearestPickUpDate,
        LocalDateTime paidAt
) {
}
