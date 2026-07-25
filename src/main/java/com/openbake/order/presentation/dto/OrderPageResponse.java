package com.openbake.order.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 주문 목록 응답. Spring Page 를 그대로 직렬화하면 pageable·sort 같은 내부 필드가
 * 응답에 새므로 명세서 모양대로 감싼다.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderPageResponse {
    private List<OrderSummaryResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
