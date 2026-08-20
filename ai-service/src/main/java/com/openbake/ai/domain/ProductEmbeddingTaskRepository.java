package com.openbake.ai.domain;

import java.util.Optional;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ProductEmbeddingTaskRepository {

    Optional<ProductEmbeddingTask> findLockedByProductId(Long productId);

    Optional<ProductEmbeddingTask> findById(Long id);

    Optional<ProductEmbeddingTask> findLockedById(Long id);

    Optional<ProductEmbeddingTask> claimNext();

    ProductEmbeddingTask save(ProductEmbeddingTask task);

    List<ProductEmbeddingTask> findRecoverable(Instant now);

    List<ProductEmbeddingTask> findAllById(Collection<Long> ids);

    long countByStatus(EmbeddingTaskStatus status);
}
