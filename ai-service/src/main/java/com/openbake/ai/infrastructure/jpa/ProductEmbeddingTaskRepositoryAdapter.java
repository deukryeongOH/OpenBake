package com.openbake.ai.infrastructure.jpa;

import com.openbake.ai.domain.ProductEmbeddingTask;
import com.openbake.ai.domain.ProductEmbeddingTaskRepository;
import com.openbake.ai.domain.EmbeddingTaskStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProductEmbeddingTaskRepositoryAdapter implements ProductEmbeddingTaskRepository {

    private final ProductEmbeddingTaskJpaRepository jpaRepository;

    @Override
    public Optional<ProductEmbeddingTask> findLockedByProductId(Long productId) {
        return jpaRepository.findLockedByProductId(productId);
    }

    @Override
    public Optional<ProductEmbeddingTask> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<ProductEmbeddingTask> findLockedById(Long id) {
        return jpaRepository.findLockedById(id);
    }

    @Override
    public Optional<ProductEmbeddingTask> claimNext() {
        return jpaRepository.claimNext();
    }

    @Override
    public ProductEmbeddingTask save(ProductEmbeddingTask task) {
        return jpaRepository.save(task);
    }

    @Override
    public List<ProductEmbeddingTask> findRecoverable(Instant now) {
        return jpaRepository.findRecoverable(now);
    }

    @Override
    public List<ProductEmbeddingTask> findAllById(Collection<Long> ids) {
        return jpaRepository.findAllById(ids);
    }

    @Override
    public long countByStatus(EmbeddingTaskStatus status) {
        return jpaRepository.countByStatus(status);
    }

    @Override
    public long countExpiredLease(Instant now) {
        return jpaRepository.countExpiredLease(now);
    }

    @Override
    public Optional<Instant> findOldestPendingCreatedAt() {
        return jpaRepository.findOldestPendingCreatedAt();
    }
}
