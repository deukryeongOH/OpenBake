package com.openbake.ai.application.port;

import java.time.Instant;
import java.util.List;

public interface ProductEmbeddingIndex {

    boolean exists(Long productId);

    void upsert(ProductEmbeddingIndexDocument document);

    void touch(Long productId, Instant sourceOccurredAt);

    void delete(Long productId);

    /** 정합성 검사 용도로 대상 인덱스의 모든 productId를 반환한다. */
    List<Long> findAllProductIds();

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
