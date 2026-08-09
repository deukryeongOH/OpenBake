package com.openbake.order.presentation.dto;

import com.openbake.order.application.SellerOrderPageResult;

import java.util.List;

//판매자 판매내역 목록 응답. OrderPageResponse 와 대칭 구조.
public record SellerOrderPageResponse(
        List<SellerOrderSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static SellerOrderPageResponse from(SellerOrderPageResult result) {
        return result == null ? null : new SellerOrderPageResponse(
                result.content().stream()
                        .map(SellerOrderSummaryResponse::from)
                        .toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages()
        );
    }
}