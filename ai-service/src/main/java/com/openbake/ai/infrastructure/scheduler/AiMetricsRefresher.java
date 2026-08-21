package com.openbake.ai.infrastructure.scheduler;

import com.openbake.ai.domain.EmbeddingTaskStatus;
import com.openbake.ai.domain.ProductEmbeddingTaskRepository;
import com.openbake.ai.infrastructure.jpa.MemberProductInteractionJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AiMetricsRefresher {

    private final ProductEmbeddingTaskRepository taskRepository;
    private final MemberProductInteractionJpaRepository interactionRepository;
    private final Map<EmbeddingTaskStatus, AtomicLong> taskCounts =
            new EnumMap<>(EmbeddingTaskStatus.class);
    private final AtomicLong interactionRows = new AtomicLong();

    public AiMetricsRefresher(
            ProductEmbeddingTaskRepository taskRepository,
            MemberProductInteractionJpaRepository interactionRepository,
            MeterRegistry registry) {
        this.taskRepository = taskRepository;
        this.interactionRepository = interactionRepository;
        for (EmbeddingTaskStatus status : EmbeddingTaskStatus.values()) {
            AtomicLong count = new AtomicLong();
            taskCounts.put(status, count);
            registry.gauge(
                    "openbake.ai.embedding.tasks", Tags.of("status", status.name()), count);
        }
        // 보관 정리 배치가 행동 이벤트 유입량을 따라가지 못하는지를 감지한다.
        registry.gauge("openbake.ai.interactions.rows", interactionRows);
    }

    @Scheduled(
            fixedDelayString = "${openbake.ai.metrics.refresh-interval:PT30S}",
            initialDelayString = "${openbake.ai.metrics.refresh-interval:PT30S}")
    public void refresh() {
        taskCounts.forEach((status, count) -> count.set(taskRepository.countByStatus(status)));
        interactionRows.set(interactionRepository.count());
    }
}
