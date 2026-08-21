package com.openbake.ai.application;

import com.openbake.ai.application.port.ProductEmbeddingIndex;
import com.openbake.ai.domain.EmbeddingTaskStatus;
import com.openbake.ai.domain.ProductChangeType;
import com.openbake.ai.domain.ProductChangedEvent;
import com.openbake.ai.domain.ProductEmbeddingMetadataRepository;
import com.openbake.ai.domain.ProductEmbeddingTask;
import com.openbake.ai.domain.ProductEmbeddingTaskRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmbeddingRecoveryScheduler {

    private static final Instant SYNTHETIC_OCCURRED_AT = Instant.EPOCH;

    private final ProductEmbeddingTaskRepository taskRepository;
    private final ProductEmbeddingMetadataRepository metadataRepository;
    private final ProductEmbeddingIndex embeddingIndex;
    private final EmbeddingTextBuilder textBuilder;
    private final EmbeddingProperties embeddingProperties;

    @Transactional
    public ScheduleResult schedule(CoreProductSource product, boolean skipWhenCurrent) {
        String sourceHash = textBuilder.build(product).sourceHash();
        var existing = taskRepository.findLockedByProductId(product.productId());
        if (skipWhenCurrent && existing.isPresent()) {
            ProductEmbeddingTask task = existing.get();
            boolean current = task.getStatus() == EmbeddingTaskStatus.COMPLETED
                    && sourceHash.equals(task.getSourceHash())
                    && metadataRepository.findById(product.productId())
                        .filter(metadata -> metadata.matches(
                                sourceHash,
                                embeddingProperties.model(),
                                embeddingProperties.dimensions(),
                                embeddingProperties.indexVersion()))
                        .isPresent()
                    && embeddingIndex.exists(product.productId());
            if (current) {
                return ScheduleResult.SKIPPED;
            }
        }

        if (existing.isPresent()) {
            existing.get().rescheduleFromRecovery(
                    product.name(), product.description(), product.category(), product.type(), Instant.now());
        } else {
            ProductChangedEvent event = new ProductChangedEvent(
                    syntheticEventId(product.productId()),
                    1,
                    ProductChangeType.CREATED,
                    SYNTHETIC_OCCURRED_AT,
                    product.productId(),
                    product.name(),
                    product.description(),
                    product.category(),
                    product.type());
            taskRepository.save(ProductEmbeddingTask.from(event, Instant.now()));
        }
        return ScheduleResult.SCHEDULED;
    }

    private UUID syntheticEventId(Long productId) {
        return UUID.nameUUIDFromBytes(
                ("openbake-backfill:" + productId).getBytes(StandardCharsets.UTF_8));
    }

    public enum ScheduleResult {
        SCHEDULED,
        SKIPPED
    }
}
