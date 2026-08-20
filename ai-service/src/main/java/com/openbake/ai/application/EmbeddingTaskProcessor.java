package com.openbake.ai.application;

import com.openbake.ai.application.EmbeddingTaskSnapshot.TaskSnapshot;
import com.openbake.ai.application.EmbeddingTextBuilder.SourceText;
import com.openbake.ai.application.port.EmbeddingClient;
import com.openbake.ai.application.port.ProductEmbeddingIndex;
import com.openbake.ai.domain.ProductChangeType;
import java.util.List;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingTaskProcessor {

    private final EmbeddingTaskClaimer taskClaimer;
    private final EmbeddingTaskFinalizer taskFinalizer;
    private final EmbeddingTextBuilder textBuilder;
    private final EmbeddingClient embeddingClient;
    private final ProductEmbeddingIndex embeddingIndex;
    private final MeterRegistry meterRegistry;

    @Autowired
    public EmbeddingTaskProcessor(
            EmbeddingTaskClaimer taskClaimer,
            EmbeddingTaskFinalizer taskFinalizer,
            EmbeddingTextBuilder textBuilder,
            EmbeddingClient embeddingClient,
            ProductEmbeddingIndex embeddingIndex,
            MeterRegistry meterRegistry) {
        this.taskClaimer = taskClaimer;
        this.taskFinalizer = taskFinalizer;
        this.textBuilder = textBuilder;
        this.embeddingClient = embeddingClient;
        this.embeddingIndex = embeddingIndex;
        this.meterRegistry = meterRegistry;
    }

    /** 기존 단위 테스트 및 직접 생성 호출부 호환용이다. */
    public EmbeddingTaskProcessor(
            EmbeddingTaskClaimer taskClaimer,
            EmbeddingTaskFinalizer taskFinalizer,
            EmbeddingTextBuilder textBuilder,
            EmbeddingClient embeddingClient,
            ProductEmbeddingIndex embeddingIndex) {
        this(taskClaimer, taskFinalizer, textBuilder, embeddingClient, embeddingIndex,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

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
            Timer.Sample sample = Timer.start(meterRegistry);
            try {
                List<Float> vector = embeddingClient.embed(source.text());
                taskFinalizer.upsertAndComplete(task, source.sourceHash(), vector);
            } finally {
                sample.stop(meterRegistry.timer("openbake.ai.embedding.duration"));
            }
        } catch (EmbeddingFailureException failure) {
            meterRegistry.counter(
                    "openbake.ai.embedding.failures", "errorCode", failure.getErrorCode()).increment();
            taskFinalizer.handleFailure(task, failure);
        } catch (Exception failure) {
            meterRegistry.counter(
                    "openbake.ai.embedding.failures", "errorCode", "EMBEDDING_WORKER_ERROR").increment();
            taskFinalizer.handleFailure(
                    task, EmbeddingFailureException.transientFailure("EMBEDDING_WORKER_ERROR", failure));
        }
        return true;
    }
}
