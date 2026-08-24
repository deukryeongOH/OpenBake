package com.openbake.order.presentation.dto;

import com.openbake.order.application.OrderDetailResult;
import com.openbake.order.domain.OrderItemStatus;
import com.openbake.order.domain.OrderState;
import com.openbake.order.domain.SalesType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 상세 응답. 주문 내역 화면이라 판매자 상호명·단가까지 전부 내려준다.
 *
 * 판매자·픽업일·확정 시각이 항목 안에 있다 — 한 주문에 판매자가 여럿일 수 있다.
 */
public record OrderDetailResponse(
        Long orderId,
        OrderState orderState,
        @Schema(description = "GENERAL(일반 상품) 또는 DROP", example = "GENERAL")
        SalesType salesType,
        List<Item> items,
        BigDecimal totalAmount,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        LocalDateTime canceledAt,
        @Schema(description = "PENDING 일 때만 의미가 있다. 이 시각까지 결제하지 않으면 자동 취소된다.")
        LocalDateTime reservationExpiresAt
) {

    public record Item(
            Long orderItemId,
            Long productId,
            @Schema(description = "드롭 주문에서만 채워진다.")
            Long dropId,
            String productName,
            String imageUrl,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal subtotal,
            LocalDate pickUpDate,
            @Schema(description = "항목별 구매확정 상태", example = "UNCONFIRMED")
            OrderItemStatus itemStatus,
            @Schema(description = "이 항목의 구매확정 시각. 판매자가 아직 누르지 않았으면 null.")
            LocalDateTime confirmedAt,
            Seller seller
    ) {
    }

    /**
     * 상호명은 주문 시점 스냅샷, 주소·연락처는 조회 시점 최신값이다.
     * 값이 null 이면 프론트는 지도·전화 버튼을 비활성화한다.
     */
    public record Seller(
            Long sellerId,
            String sellerName,
            String address,
            String phoneNumber
    ) {
    }

    public static OrderDetailResponse from(OrderDetailResult result) {
        return new OrderDetailResponse(
                result.orderId(),
                result.orderState(),
                result.salesType(),
                result.items().stream().map(OrderDetailResponse::toItem).toList(),
                result.totalAmount(),
                result.createdAt(),
                result.paidAt(),
                result.canceledAt(),
                result.reservationExpiresAt()
        );
    }

    private static Item toItem(OrderDetailResult.OrderItemInfo item) {
        OrderDetailResult.SellerInfo seller = item.seller();
        return new Item(
                item.orderItemId(),
                item.productId(),
                item.dropId(),
                item.productName(),
                item.imageUrl(),
                item.unitPrice(),
                item.quantity(),
                item.subtotal(),
                item.pickUpDate(),
                item.itemStatus(),
                item.confirmedAt(),
                new Seller(seller.sellerId(), seller.sellerName(), seller.address(), seller.phoneNumber())
        );
    }
}
