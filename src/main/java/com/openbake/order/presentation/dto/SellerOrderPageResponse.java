package com.openbake.order.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

//판매자 판매내역 목록 응답. OrderPageResponse 와 대칭 구조.
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SellerOrderPageResponse {
    private List<SellerOrderSummaryResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
