package com.openbake.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openbake.ai.application.EmbeddingRecoveryScheduler.ScheduleResult;
import com.openbake.ai.application.port.ProductEmbeddingIndex;
import com.openbake.ai.domain.ProductChangeType;
import com.openbake.ai.domain.ProductChangedEvent;
import com.openbake.ai.domain.ProductEmbeddingMetadata;
import com.openbake.ai.domain.ProductEmbeddingMetadataRepository;
import com.openbake.ai.domain.ProductEmbeddingTask;
import com.openbake.ai.domain.ProductEmbeddingTaskRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmbeddingRecoverySchedulerTest {

    private final ProductEmbeddingTaskRepository tasks = mock(ProductEmbeddingTaskRepository.class);
    private final ProductEmbeddingMetadataRepository metadata = mock(ProductEmbeddingMetadataRepository.class);
    private final ProductEmbeddingIndex index = mock(ProductEmbeddingIndex.class);
    private final EmbeddingTextBuilder textBuilder = new EmbeddingTextBuilder();
    private final EmbeddingProperties properties = new EmbeddingProperties(
            "model", 3, "write-index", "search-index", "v2",
            Duration.ofSeconds(1), Duration.ofSeconds(1),
            new EmbeddingProperties.Worker(Duration.ofSeconds(1), 1, Duration.ofMinutes(1)));
    private final EmbeddingRecoveryScheduler scheduler = new EmbeddingRecoveryScheduler(
            tasks, metadata, index, textBuilder, properties);

    @Test
    void skipsOnlyCompletedTaskWithMatchingMetadataAndExistingDocument() {
        CoreProductSource product = product();
        String hash = textBuilder.build(product).sourceHash();
        ProductEmbeddingTask task = ProductEmbeddingTask.from(event(product), Instant.now());
        task.recordSourceHash(hash, Instant.now());
        task.complete(Instant.now());
        ProductEmbeddingMetadata current = ProductEmbeddingMetadata.create(
                1L, hash, "model", 3, "v2", Instant.now(), Instant.now());
        when(tasks.findLockedByProductId(1L)).thenReturn(Optional.of(task));
        when(metadata.findById(1L)).thenReturn(Optional.of(current));
        when(index.exists(1L)).thenReturn(true);

        assertThat(scheduler.schedule(product, true)).isEqualTo(ScheduleResult.SKIPPED);
        verify(tasks, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void reschedulesWhenElasticsearchDocumentIsMissing() {
        CoreProductSource product = product();
        String hash = textBuilder.build(product).sourceHash();
        ProductEmbeddingTask task = ProductEmbeddingTask.from(event(product), Instant.now());
        task.recordSourceHash(hash, Instant.now());
        task.complete(Instant.now());
        when(tasks.findLockedByProductId(1L)).thenReturn(Optional.of(task));
        when(metadata.findById(1L)).thenReturn(Optional.of(ProductEmbeddingMetadata.create(
                1L, hash, "model", 3, "v2", Instant.now(), Instant.now())));
        when(index.exists(1L)).thenReturn(false);

        assertThat(scheduler.schedule(product, true)).isEqualTo(ScheduleResult.SCHEDULED);
        assertThat(task.getStatus().name()).isEqualTo("PENDING");
        assertThat(task.getRetryCount()).isZero();
    }

    private CoreProductSource product() {
        return new CoreProductSource(1L, "bread", "description", "MEAL_BREADS", "GENERAL");
    }

    private ProductChangedEvent event(CoreProductSource product) {
        return new ProductChangedEvent(
                UUID.randomUUID(), 1, ProductChangeType.CREATED, Instant.now(),
                product.productId(), product.name(), product.description(), product.category(), product.type());
    }
}
