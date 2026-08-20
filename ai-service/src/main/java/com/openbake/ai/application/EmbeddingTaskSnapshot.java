package com.openbake.ai.application;

import com.openbake.ai.domain.ProductChangeType;
import com.openbake.ai.domain.ProductEmbeddingTask;
import java.time.Instant;
import java.util.UUID;

public final class EmbeddingTaskSnapshot {

    private EmbeddingTaskSnapshot() {
    }

    public static TaskSnapshot from(ProductEmbeddingTask task) {
        return new TaskSnapshot(
                task.getId(),
                task.getProductId(),
                task.getLatestEventId(),
                task.getEventType(),
                task.getName(),
                task.getDescription(),
                task.getCategory(),
                task.getProductType(),
                task.getSourceOccurredAt(),
                task.getRetryCount());
    }

    public record TaskSnapshot(
            Long taskId,
            Long productId,
            UUID latestEventId,
            ProductChangeType eventType,
            String name,
            String description,
            String category,
            String productType,
            Instant sourceOccurredAt,
            int retryCount) {
    }
}
