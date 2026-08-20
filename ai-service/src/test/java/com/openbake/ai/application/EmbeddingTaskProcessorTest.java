package com.openbake.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.openbake.ai.application.EmbeddingTaskSnapshot.TaskSnapshot;
import com.openbake.ai.application.EmbeddingTextBuilder.SourceText;
import com.openbake.ai.application.port.EmbeddingClient;
import com.openbake.ai.application.port.ProductEmbeddingIndex;
import com.openbake.ai.domain.ProductChangeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmbeddingTaskProcessorTest {

    @Mock
    private EmbeddingTaskClaimer taskClaimer;
    @Mock
    private EmbeddingTaskFinalizer taskFinalizer;
    @Mock
    private EmbeddingTextBuilder textBuilder;
    @Mock
    private EmbeddingClient embeddingClient;
    @Mock
    private ProductEmbeddingIndex embeddingIndex;

    @Test
    void invalidatedTaskSkipsEmbeddingClientCall() {
        TaskSnapshot snapshot = new TaskSnapshot(
                1L, 10L, UUID.randomUUID(), ProductChangeType.UPDATED,
                "name", "description", "MEAL_BREADS", "GENERAL", Instant.now(), 0);
        SourceText source = new SourceText("embedding source", "source-hash");
        given(taskClaimer.claimNext()).willReturn(Optional.of(snapshot));
        given(textBuilder.build(snapshot)).willReturn(source);
        given(taskFinalizer.metadataMatches(snapshot, source.sourceHash())).willReturn(false);
        given(taskFinalizer.isStillCurrent(snapshot)).willReturn(false);
        EmbeddingTaskProcessor processor = new EmbeddingTaskProcessor(
                taskClaimer, taskFinalizer, textBuilder, embeddingClient, embeddingIndex);

        boolean processed = processor.processNext();

        assertThat(processed).isTrue();
        verifyNoInteractions(embeddingClient, embeddingIndex);
        verify(taskFinalizer, never()).upsertAndComplete(any(), any(), any());
    }
}
