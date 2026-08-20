package com.openbake.ai.domain;

import java.util.Optional;

public interface ProductEmbeddingTaskRepository {

    Optional<ProductEmbeddingTask> findLockedByProductId(Long productId);

    Optional<ProductEmbeddingTask> findById(Long id);

    Optional<ProductEmbeddingTask> findLockedById(Long id);

    Optional<ProductEmbeddingTask> claimNext();

    ProductEmbeddingTask save(ProductEmbeddingTask task);
}
