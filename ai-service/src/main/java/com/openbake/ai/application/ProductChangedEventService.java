package com.openbake.ai.application;

import com.openbake.ai.domain.ConsumedEventRepository;
import com.openbake.ai.domain.ProductChangedEvent;
import com.openbake.ai.domain.ProductEmbeddingTask;
import com.openbake.ai.domain.ProductEmbeddingTaskRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductChangedEventService {

    private final ConsumedEventRepository consumedEventRepository;
    private final ProductEmbeddingTaskRepository taskRepository;

    @Transactional
    public void consume(ProductChangedEvent event, String topic, int partition, long offset) {
        event.validate();
        Instant now = Instant.now();

        int claimed = consumedEventRepository.claim(
                event.eventId(),
                topic,
                partition,
                offset,
                event.eventType().name(),
                event.occurredAt(),
                now);
        if (claimed == 0) {
            return;
        }

        taskRepository.findLockedByProductId(event.productId())
                .ifPresentOrElse(
                        task -> applyWhenCurrent(task, event, now),
                        () -> taskRepository.save(ProductEmbeddingTask.from(event, now)));
    }

    private void applyWhenCurrent(ProductEmbeddingTask task, ProductChangedEvent event, Instant now) {
        if (event.occurredAt().isBefore(task.getSourceOccurredAt())) {
            return;
        }
        task.apply(event, now);
    }
}
