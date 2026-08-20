package com.openbake.ai.application.port;

import java.time.Instant;
import java.util.List;

public interface ProductEmbeddingIndex {

    boolean exists(Long productId);

    void upsert(ProductEmbeddingIndexDocument document);

    void touch(Long productId, Instant sourceOccurredAt);

    void delete(Long productId);

    record ProductEmbeddingIndexDocument(
            Long productId,
            String name,
            String description,
            String category,
            String type,
            List<Float> embedding,
            String sourceHash,
            String embeddingModel,
            String indexVersion,
            Instant sourceOccurredAt,
            Instant embeddedAt) {
    }
}
