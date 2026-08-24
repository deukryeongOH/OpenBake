package com.openbake.order.application;

import com.openbake.order.domain.OrderItemStatus;
import com.openbake.order.domain.OrderState;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 판매자 판매내역 한 줄.
 *
 * <b>남의 항목은 담지 않는다.</b> 한 주문에 판매자가 여럿일 수 있으므로 자기 sellerId
 * 항목만 걸러 담고, 금액도 주문 전체가 아니라 <b>자기 몫 소계</b>다.
 * 주문 전체 금액을 보여주면 남의 매출까지 자기 화면에 뜬다.
 */
public record SellerOrderSummaryResult(
        Long orderId,
        String buyerName,
        OrderState orderState,
        //자기 항목들의 소계 합.
        BigDecimal sellerAmount,
        LocalDateTime paidAt,
        LocalDateTime canceledAt,
        List<SellerOrderItem> items
) {

    public record SellerOrderItem(
            Long orderItemId,
            Long productId,
            Long dropId,
            String productName,
            int quantity,
            BigDecimal subtotal,
            LocalDate pickUpDate,
            OrderItemStatus itemStatus,
            //이 항목을 확정한 시각. null 이면 아직 확정 버튼을 누르지 않았다.
            LocalDateTime confirmedAt
    ) {
    }
}
