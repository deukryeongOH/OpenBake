package com.openbake.order.application;

import java.util.List;

public record SellerOrderPageResult(
        List<SellerOrderSummaryResult> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}