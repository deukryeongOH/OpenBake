package com.openbake.order.presentation.dto;

import com.openbake.order.domain.OrderState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

//구매확정 응답(판매자).
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderConfirmResponse {
    private Long orderId;
    private OrderState orderState;
    private LocalDateTime confirmedAt;
}
