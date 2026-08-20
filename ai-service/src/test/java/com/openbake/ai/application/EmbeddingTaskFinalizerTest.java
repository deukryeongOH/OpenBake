package com.openbake.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;

import com.openbake.ai.application.EmbeddingTaskSnapshot.TaskSnapshot;
import com.openbake.ai.application.port.ProductEmbeddingIndex;
import com.openbake.ai.domain.EmbeddingTaskStatus;
import com.openbake.ai.domain.ProductChangeType;
import com.openbake.ai.domain.ProductChangedEvent;
import com.openbake.ai.domain.ProductEmbeddingMetadataRepository;
import com.openbake.ai.domain.ProductEmbeddingTask;
import com.openbake.ai.domain.ProductEmbeddingTaskRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
class EmbeddingTaskFinalizerTest {

    @Mock
    private ProductEmbeddingTaskRepository taskRepository;
    @Mock
    private ProductEmbeddingMetadataRepository metadataRepository;
    @Mock
    private ProductEmbeddingIndex embeddingIndex;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TransactionStatus transactionStatus;

    private EmbeddingTaskFinalizer finalizer;
    private ProductEmbeddingTask task;
    private TaskSnapshot snapshot;

    @BeforeEach
    void setUp() {
        var properties = new EmbeddingProperties(
                "text-embedding-3-small",
                1536,
                "product-embeddings-v1",
                "v1",
                Duration.ofSeconds(3),
                Duration.ofSeconds(30),
                new EmbeddingProperties.Worker(Duration.ofSeconds(2), 20, Duration.ofMinutes(5)));
        finalizer = new EmbeddingTaskFinalizer(
                taskRepository, metadataRepository, embeddingIndex, properties, transactionManager);
        lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        ProductChangedEvent event = new ProductChangedEvent(
                UUID.randomUUID(), 1, ProductChangeType.CREATED, Instant.now(), 10L,
                "name", "description", "MEAL_BREADS", "GENERAL");
        task = ProductEmbeddingTask.from(event, Instant.now());
        task.claim(Instant.now(), Duration.ofMinutes(5));
        snapshot = new TaskSnapshot(
                1L, 10L, event.eventId(), ProductChangeType.CREATED,
                "name", "description", "MEAL_BREADS", "GENERAL", event.occurredAt(), 0);
        lenient().when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        lenient().when(taskRepository.findLockedById(1L)).thenReturn(Optional.of(task));
    }

    @Test
    void transientFailureUsesRetryAfterWhenItIsLongerThanDefaultDelay() {
        Instant before = Instant.now();

        finalizer.handleFailure(snapshot, EmbeddingFailureException.transientFailure(
                "OPENAI_RATE_LIMIT", Duration.ofMinutes(2), null));

        assertThat(task.getStatus()).isEqualTo(EmbeddingTaskStatus.PENDING);
        assertThat(task.getRetryCount()).isEqualTo(1);
        assertThat(task.getNextAttemptAt()).isAfterOrEqualTo(before.plus(Duration.ofMinutes(2)));
        verifyNoInteractions(embeddingIndex, metadataRepository);
    }

    @Test
    void fourthTransientFailureMovesTaskToFailed() {
        for (int retry = 0; retry < 3; retry++) {
            finalizer.handleFailure(
                    snapshot, EmbeddingFailureException.transientFailure("OPENAI_SERVER_ERROR", null));
            task.claim(Instant.now(), Duration.ofMinutes(5));
        }

        finalizer.handleFailure(
                snapshot, EmbeddingFailureException.transientFailure("OPENAI_SERVER_ERROR", null));

        assertThat(task.getRetryCount()).isEqualTo(3);
        assertThat(task.getStatus()).isEqualTo(EmbeddingTaskStatus.FAILED);
        assertThat(task.getLastErrorCode()).isEqualTo("OPENAI_SERVER_ERROR");
    }

    @Test
    void permanentFailureMovesTaskToFailedImmediately() {
        finalizer.handleFailure(
                snapshot, EmbeddingFailureException.permanentFailure("OPENAI_REQUEST_REJECTED", null));

        assertThat(task.getRetryCount()).isZero();
        assertThat(task.getStatus()).isEqualTo(EmbeddingTaskStatus.FAILED);
    }

    @Test
    void upsertDoesNothingWhenANewerEventInvalidatedTheSnapshot() {
        ProductChangedEvent newerEvent = new ProductChangedEvent(
                UUID.randomUUID(), 1, ProductChangeType.UPDATED, Instant.now(), 10L,
                "new name", "new description", "MEAL_BREADS", "GENERAL");
        task.apply(newerEvent, Instant.now());
        task.claim(Instant.now(), Duration.ofMinutes(5));

        finalizer.upsertAndComplete(snapshot, "old-hash", java.util.List.of(0.1f));

        assertThat(task.getStatus()).isEqualTo(EmbeddingTaskStatus.PROCESSING);
        assertThat(task.getLatestEventId()).isEqualTo(newerEvent.eventId());
        assertThat(task.getSourceHash()).isNull();
        verifyNoInteractions(embeddingIndex, metadataRepository);
    }

    @Test
    void failureDoesNothingWhenANewerEventInvalidatedTheSnapshot() {
        ProductChangedEvent newerEvent = new ProductChangedEvent(
                UUID.randomUUID(), 1, ProductChangeType.UPDATED, Instant.now(), 10L,
                "new name", "new description", "MEAL_BREADS", "GENERAL");
        task.apply(newerEvent, Instant.now());
        task.claim(Instant.now(), Duration.ofMinutes(5));

        finalizer.handleFailure(
                snapshot, EmbeddingFailureException.transientFailure("OPENAI_SERVER_ERROR", null));

        assertThat(task.getStatus()).isEqualTo(EmbeddingTaskStatus.PROCESSING);
        assertThat(task.getRetryCount()).isZero();
        assertThat(task.getLastErrorCode()).isNull();
        verifyNoInteractions(embeddingIndex, metadataRepository);
    }
}
