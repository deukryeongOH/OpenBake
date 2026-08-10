package com.openbake.order.application;

import java.util.List;

public record OrderPageResult(
        List<OrderSummaryResult> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}