package com.openbake.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.openbake.ai.domain.ConsumedEventRepository;
import com.openbake.ai.domain.ProductChangeType;
import com.openbake.ai.domain.ProductChangedEvent;
import com.openbake.ai.domain.ProductEmbeddingTask;
import com.openbake.ai.domain.ProductEmbeddingTaskRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductChangedEventServiceTest {

    @Mock
    private ConsumedEventRepository consumedEventRepository;
    @Mock
    private ProductEmbeddingTaskRepository taskRepository;

    @Test
    void duplicateEventDoesNotTouchTask() {
        ProductChangedEvent event = event(UUID.randomUUID(), Instant.parse("2026-08-20T00:00:00Z"), "name");
        given(consumedEventRepository.claim(
                any(), anyString(), anyInt(), anyLong(), anyString(), any(), any())).willReturn(0);
        ProductChangedEventService service = new ProductChangedEventService(consumedEventRepository, taskRepository);

        service.consume(event, "product.changed.v1", 0, 1L);

        verify(taskRepository, never()).findLockedByProductId(anyLong());
    }

    @Test
    void olderEventIsRecordedButDoesNotOverwriteLatestTask() {
        Instant latestTime = Instant.parse("2026-08-20T02:00:00Z");
        ProductEmbeddingTask task = ProductEmbeddingTask.from(
                event(UUID.randomUUID(), latestTime, "latest"), Instant.now());
        given(consumedEventRepository.claim(
                any(), anyString(), anyInt(), anyLong(), anyString(), any(), any())).willReturn(1);
        given(taskRepository.findLockedByProductId(10L)).willReturn(Optional.of(task));
        ProductChangedEventService service = new ProductChangedEventService(consumedEventRepository, taskRepository);

        service.consume(
                event(UUID.randomUUID(), latestTime.minusSeconds(1), "old"),
                "product.changed.v1", 0, 2L);

        assertThat(task.getName()).isEqualTo("latest");
        assertThat(task.getSourceOccurredAt()).isEqualTo(latestTime);
    }

    @Test
    void equalOccurredAtTreatsLaterArrivalAsLatest() {
        Instant occurredAt = Instant.parse("2026-08-20T02:00:00Z");
        ProductEmbeddingTask task = ProductEmbeddingTask.from(
                event(UUID.randomUUID(), occurredAt, "first"), Instant.now());
        ProductChangedEvent later = event(UUID.randomUUID(), occurredAt, "later");
        given(consumedEventRepository.claim(
                any(), anyString(), anyInt(), anyLong(), anyString(), any(), any())).willReturn(1);
        given(taskRepository.findLockedByProductId(10L)).willReturn(Optional.of(task));
        ProductChangedEventService service = new ProductChangedEventService(consumedEventRepository, taskRepository);

        service.consume(later, "product.changed.v1", 0, 3L);

        assertThat(task.getName()).isEqualTo("later");
        assertThat(task.getLatestEventId()).isEqualTo(later.eventId());
    }

    private ProductChangedEvent event(UUID id, Instant occurredAt, String name) {
        return new ProductChangedEvent(
                id, 1, ProductChangeType.UPDATED, occurredAt, 10L,
                name, "description", "MEAL_BREADS", "GENERAL");
    }
}
