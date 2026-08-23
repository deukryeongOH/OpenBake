package com.openbake.product.presentation.dto;

import com.openbake.product.infrastructure.elasticsearch.ProductReindexScheduler;

public record ProductReindexResultResponse(int upsertCount, int orphanDeletedCount) {

    public static ProductReindexResultResponse from(ProductReindexScheduler.ReindexResult result) {
        return new ProductReindexResultResponse(result.upsertCount(), result.orphanDeletedCount());
    }
}
