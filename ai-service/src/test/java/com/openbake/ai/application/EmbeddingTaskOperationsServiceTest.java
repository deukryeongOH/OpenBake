package com.openbake.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openbake.ai.domain.ProductChangeType;
import com.openbake.ai.domain.ProductChangedEvent;
import com.openbake.ai.domain.ProductEmbeddingTask;
import com.openbake.ai.domain.ProductEmbeddingTaskRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmbeddingTaskOperationsServiceTest {

    @Test
    void retriesOnlyFailedAndExpiredProcessingAndResetsFields() {
        Instant now = Instant.parse("2026-08-20T00:00:00Z");
        ProductEmbeddingTask failed = task();
        failed.scheduleRetry("TEMP", now.minusSeconds(60), now.minusSeconds(120));
        failed.fail("FINAL", now.minusSeconds(60));
        ProductEmbeddingTask expired = task();
        expired.claim(now.minusSeconds(120), Duration.ofSeconds(30));
        ProductEmbeddingTask completed = task();
        completed.complete(now.minusSeconds(10));
        ProductEmbeddingTaskRepository repository = mock(ProductEmbeddingTaskRepository.class);
        when(repository.findLockedById(1L)).thenReturn(Optional.of(failed));
        when(repository.findLockedById(2L)).thenReturn(Optional.of(expired));
        when(repository.findLockedById(3L)).thenReturn(Optional.of(completed));
        var service = new EmbeddingTaskOperationsService(
                repository, Clock.fixed(now, ZoneOffset.UTC));

        var result = service.retry(List.of(1L, 2L, 3L, 999L));

        assertThat(result.retriedTaskIds()).containsExactly(1L, 2L);
        assertThat(result.skippedTaskIds()).containsExactly(3L, 999L);
        assertThat(failed.getRetryCount()).isZero();
        assertThat(failed.getLastErrorCode()).isNull();
        assertThat(failed.getNextAttemptAt()).isNull();
        assertThat(expired.getLeaseExpiresAt()).isNull();
        assertThat(completed.getStatus().name()).isEqualTo("COMPLETED");
    }

    private ProductEmbeddingTask task() {
        return ProductEmbeddingTask.from(new ProductChangedEvent(
                UUID.randomUUID(), 1, ProductChangeType.CREATED, Instant.EPOCH,
                1L, "bread", "description", "MEAL_BREADS", "GENERAL"), Instant.EPOCH);
    }
}
