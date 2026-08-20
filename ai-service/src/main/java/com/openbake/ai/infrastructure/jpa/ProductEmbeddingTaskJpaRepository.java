package com.openbake.ai.infrastructure.jpa;

import com.openbake.ai.domain.ProductEmbeddingTask;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductEmbeddingTaskJpaRepository extends JpaRepository<ProductEmbeddingTask, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from ProductEmbeddingTask task where task.productId = :productId")
    Optional<ProductEmbeddingTask> findLockedByProductId(@Param("productId") Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from ProductEmbeddingTask task where task.id = :id")
    Optional<ProductEmbeddingTask> findLockedById(@Param("id") Long id);

    @Query(value = """
            SELECT * FROM product_embedding_tasks
            WHERE (status = 'PENDING' AND (next_attempt_at IS NULL OR next_attempt_at <= now()))
               OR (status = 'PROCESSING' AND lease_expires_at IS NOT NULL AND lease_expires_at <= now())
            ORDER BY next_attempt_at NULLS FIRST, id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<ProductEmbeddingTask> claimNext();
}
