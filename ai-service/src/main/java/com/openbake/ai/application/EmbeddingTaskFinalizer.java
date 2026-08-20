package com.openbake.ai.application;

import com.openbake.ai.application.EmbeddingTaskSnapshot.TaskSnapshot;
import com.openbake.ai.application.port.ProductEmbeddingIndex;
import com.openbake.ai.application.port.ProductEmbeddingIndex.ProductEmbeddingIndexDocument;
import com.openbake.ai.domain.EmbeddingTaskStatus;
import com.openbake.ai.domain.ProductEmbeddingMetadata;
import com.openbake.ai.domain.ProductEmbeddingMetadataRepository;
import com.openbake.ai.domain.ProductEmbeddingTask;
import com.openbake.ai.domain.ProductEmbeddingTaskRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class EmbeddingTaskFinalizer {

    private static final List<Duration> RETRY_DELAYS = List.of(
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30));

    private final ProductEmbeddingTaskRepository taskRepository;
    private final ProductEmbeddingMetadataRepository metadataRepository;
    private final ProductEmbeddingIndex embeddingIndex;
    private final EmbeddingProperties properties;
    private final TransactionTemplate transactionTemplate;

    public EmbeddingTaskFinalizer(
            ProductEmbeddingTaskRepository taskRepository,
            ProductEmbeddingMetadataRepository metadataRepository,
            ProductEmbeddingIndex embeddingIndex,
            EmbeddingProperties properties,
            PlatformTransactionManager transactionManager) {
        this.taskRepository = taskRepository;
        this.metadataRepository = metadataRepository;
        this.embeddingIndex = embeddingIndex;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public void recordSourceHash(TaskSnapshot snapshot, String sourceHash) {
        currentTask(snapshot).ifPresent(task -> task.recordSourceHash(sourceHash, Instant.now()));
    }

    @Transactional(readOnly = true)
    public boolean metadataMatches(TaskSnapshot snapshot, String sourceHash) {
        return metadataRepository.findById(snapshot.productId())
                .filter(metadata -> metadata.matches(
                        sourceHash,
                        properties.model(),
                        properties.dimensions(),
                        properties.indexVersion()))
                .isPresent();
    }

    @Transactional(readOnly = true)
    public boolean isStillCurrent(TaskSnapshot snapshot) {
        return taskRepository.findById(snapshot.taskId())
                .filter(task -> matchesSnapshot(task, snapshot))
                .isPresent();
    }

    public void reuseAndComplete(TaskSnapshot snapshot) {
        if (!isStillCurrentInTransaction(snapshot)) {
            return;
        }

        embeddingIndex.touch(snapshot.productId(), snapshot.sourceOccurredAt());
        transactionTemplate.executeWithoutResult(status -> currentTask(snapshot).ifPresent(task -> {
            Instant now = Instant.now();
            metadataRepository.findById(snapshot.productId())
                    .ifPresent(metadata -> metadata.touch(snapshot.sourceOccurredAt(), now));
            task.complete(now);
        }));
    }

    public void upsertAndComplete(TaskSnapshot snapshot, String sourceHash, List<Float> vector) {
        if (!isStillCurrentInTransaction(snapshot)) {
            return;
        }

        Instant embeddedAt = Instant.now();
        embeddingIndex.upsert(new ProductEmbeddingIndexDocument(
                snapshot.productId(),
                snapshot.name(),
                snapshot.description(),
                snapshot.category(),
                snapshot.productType(),
                vector,
                sourceHash,
                properties.model(),
                properties.indexVersion(),
                snapshot.sourceOccurredAt(),
                embeddedAt));

        transactionTemplate.executeWithoutResult(status -> currentTask(snapshot).ifPresent(task -> {
            ProductEmbeddingMetadata metadata = metadataRepository.findById(snapshot.productId())
                    .orElseGet(() -> ProductEmbeddingMetadata.create(
                            snapshot.productId(),
                            sourceHash,
                            properties.model(),
                            properties.dimensions(),
                            properties.indexVersion(),
                            snapshot.sourceOccurredAt(),
                            embeddedAt));
            metadata.update(
                    sourceHash,
                    properties.model(),
                    properties.dimensions(),
                    properties.indexVersion(),
                    snapshot.sourceOccurredAt(),
                    embeddedAt);
            metadataRepository.save(metadata);
            task.complete(embeddedAt);
        }));
    }

    public void deleteAndComplete(TaskSnapshot snapshot) {
        if (!isStillCurrentInTransaction(snapshot)) {
            return;
        }

        embeddingIndex.delete(snapshot.productId());
        transactionTemplate.executeWithoutResult(status -> currentTask(snapshot).ifPresent(task -> {
            Instant now = Instant.now();
            metadataRepository.deleteById(snapshot.productId());
            task.complete(now);
        }));
    }

    @Transactional
    public void handleFailure(TaskSnapshot snapshot, EmbeddingFailureException failure) {
        currentTask(snapshot).ifPresent(task -> {
            Instant now = Instant.now();
            if (!failure.isRetryable() || task.getRetryCount() >= RETRY_DELAYS.size()) {
                task.fail(failure.getErrorCode(), now);
                return;
            }
            Duration defaultDelay = RETRY_DELAYS.get(task.getRetryCount());
            Duration retryAfter = failure.getRetryAfter();
            Duration delay = retryAfter != null && retryAfter.compareTo(defaultDelay) > 0
                    ? retryAfter
                    : defaultDelay;
            task.scheduleRetry(failure.getErrorCode(), now.plus(delay), now);
        });
    }

    private java.util.Optional<ProductEmbeddingTask> currentTask(TaskSnapshot snapshot) {
        return taskRepository.findLockedById(snapshot.taskId())
                .filter(task -> matchesSnapshot(task, snapshot));
    }

    private boolean matchesSnapshot(ProductEmbeddingTask task, TaskSnapshot snapshot) {
        return task.getLatestEventId().equals(snapshot.latestEventId())
                && task.getStatus() == EmbeddingTaskStatus.PROCESSING;
    }

    private boolean isStillCurrentInTransaction(TaskSnapshot snapshot) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> isStillCurrent(snapshot)));
    }
}
