package com.openbake.order.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(description = "주문 목록. 최신순.")
    private List<OrderSummaryResponse> content;

    @Schema(description = "현재 페이지 번호", example = "0")
    private int page;

    @Schema(description = "실제 적용된 페이지 크기. 요청이 50을 넘으면 50으로 잘린 값이 담긴다.", example = "10")
    private int size;

    @Schema(description = "조건에 해당하는 전체 주문 수", example = "37")
    private long totalElements;

    @Schema(description = "전체 페이지 수", example = "4")
    private int totalPages;
}
