package com.openbake.order.presentation.dto;

import com.openbake.order.domain.OrderState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

//주문 목록의 한 항목. dropName·quantity 는 order_items, sellerName 은 seller 조회.
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderSummaryResponse {
    @Schema(description = "주문 ID. 상세/취소/확정 호출에 쓴다.", example = "101")
    private Long orderId;

    @Schema(description = "주문 시점 상품명(스냅샷)", example = "말차 크루아상")
    private String dropName;

    @Schema(description = "베이커리 상호명. 조회 시점 값이며 판매자를 찾지 못하면 null.", example = "오픈베이크 연남")
    private String sellerName;

    @Schema(description = "수량", example = "2")
    private int quantity;

    @Schema(description = "총 결제 금액", example = "24000")
    private BigDecimal totalAmount;

    @Schema(description = "주문 상태: PAID / CONFIRMED / CANCELED", example = "PAID")
    private OrderState orderState;

    @Schema(description = "픽업 날짜", example = "2026-08-01")
    private LocalDate pickupDate;

    @Schema(description = "결제완료 시각. 목록 정렬 기준은 주문 ID 역순이다.", example = "2026-07-28T14:05:00")
    private LocalDateTime paidAt;
}
