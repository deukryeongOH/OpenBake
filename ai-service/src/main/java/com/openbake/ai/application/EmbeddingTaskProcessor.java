package com.openbake.ai.application;

import com.openbake.ai.application.EmbeddingTaskSnapshot.TaskSnapshot;
import com.openbake.ai.application.EmbeddingTextBuilder.SourceText;
import com.openbake.ai.application.port.EmbeddingClient;
import com.openbake.ai.application.port.ProductEmbeddingIndex;
import com.openbake.ai.domain.ProductChangeType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmbeddingTaskProcessor {

    private final EmbeddingTaskClaimer taskClaimer;
    private final EmbeddingTaskFinalizer taskFinalizer;
    private final EmbeddingTextBuilder textBuilder;
    private final EmbeddingClient embeddingClient;
    private final ProductEmbeddingIndex embeddingIndex;

    public boolean processNext() {
        var claimed = taskClaimer.claimNext();
        if (claimed.isEmpty()) {
            return false;
        }

        TaskSnapshot task = claimed.get();
        try {
            if (task.eventType() == ProductChangeType.DELETED) {
                taskFinalizer.deleteAndComplete(task);
                return true;
            }

            SourceText source = textBuilder.build(task);
            taskFinalizer.recordSourceHash(task, source.sourceHash());
            if (taskFinalizer.metadataMatches(task, source.sourceHash())
                    && embeddingIndex.exists(task.productId())) {
                taskFinalizer.reuseAndComplete(task);
                return true;
            }

            if (!taskFinalizer.isStillCurrent(task)) {
                return true;
            }
            List<Float> vector = embeddingClient.embed(source.text());
            taskFinalizer.upsertAndComplete(task, source.sourceHash(), vector);
        } catch (EmbeddingFailureException failure) {
            taskFinalizer.handleFailure(task, failure);
        } catch (Exception failure) {
            taskFinalizer.handleFailure(
                    task, EmbeddingFailureException.transientFailure("EMBEDDING_WORKER_ERROR", failure));
        }
        return true;
    }
}
