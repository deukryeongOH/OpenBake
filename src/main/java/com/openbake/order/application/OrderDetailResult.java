package com.openbake.order.application;

import com.openbake.order.domain.OrderItemStatus;
import com.openbake.order.domain.OrderState;
import com.openbake.order.domain.SalesType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 상세. 주문 내역 화면이라 <b>전부 보여준다</b> — 판매자 상호명, 단가, 결제 금액까지.
 *
 * 판매자·픽업일·확정 시각이 항목 안에 있는 이유는 한 주문에 판매자가 여럿일 수 있어서다.
 */
public record OrderDetailResult(
        Long orderId,
        OrderState orderState,
        SalesType salesType,
        List<OrderItemInfo> items,
        BigDecimal totalAmount,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        LocalDateTime canceledAt,
        //PENDING 일 때만 의미가 있다. 주문서 화면이 남은 시간을 표시한다.
        LocalDateTime reservationExpiresAt
) {

    public record OrderItemInfo(
            Long orderItemId,
            Long productId,
            //드롭 주문에서만 채워진다.
            Long dropId,
            String productName,
            String imageUrl,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal subtotal,
            LocalDate pickUpDate,
            OrderItemStatus itemStatus,
            LocalDateTime confirmedAt,
            SellerInfo seller
    ) {
    }

    /**
     * 상호명은 주문 시점 스냅샷이고, 주소·연락처는 조회 시점 최신값이다.
     *
     * 옛 주소로 길을 안내하거나 옛 번호로 전화를 걸면 버튼이 하는 일 자체가 실패하기 때문이다.
     * 판매자를 못 찾으면 주소·연락처만 null 로 둔다.
     */
    public record SellerInfo(
            Long sellerId,
            String sellerName,
            String address,
            String phoneNumber
    ) {
    }
}
