package com.openbake.ai.infrastructure.scheduler;

import com.openbake.ai.application.EmbeddingProperties;
import com.openbake.ai.application.EmbeddingTaskProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmbeddingWorker {

    private final EmbeddingTaskProcessor taskProcessor;
    private final EmbeddingProperties properties;

    @Scheduled(fixedDelayString = "${openbake.ai.embedding.worker.interval:PT2S}")
    public void processBatch() {
        int processed = 0;
        while (processed < properties.worker().batchSize() && taskProcessor.processNext()) {
            processed++;
        }
    }
}
