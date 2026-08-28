package com.openbake.ai.infrastructure.scheduler;

import com.openbake.ai.domain.EmbeddingTaskStatus;
import com.openbake.ai.domain.ProductEmbeddingTaskRepository;
import com.openbake.ai.infrastructure.jpa.MemberProductInteractionJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.time.Duration;
import java.time.Instant;
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

    /**
     * lease가 만료됐는데 아직 PROCESSING인 작업 수. <b>worker 중단의 직접 신호다.</b>
     *
     * <p>worker가 죽으면 잡고 있던 작업을 놓지 못한다. 그런데 PROCESSING 건수는
     * 그대로라 정상과 구별되지 않는다. lease는 시간이 지나면 만료되므로
     * "만료됐는데 아직 PROCESSING"인 것이 곧 "잡은 채로 사라진 작업"이다.
     *
     * <p>E8 시나리오 O6(AI worker 중단)이 이 신호 없이는 성립하지 않는다.
     */
    private final AtomicLong expiredLease = new AtomicLong();

    /**
     * 가장 오래 대기 중인 작업의 나이(초).
     *
     * <p>건수만으로는 "많지만 빨리 도는 중"과 "적지만 영영 안 풀리는 중"을 구별할 수
     * 없다. 후자가 사고다. 결제 미결 지표와 같은 이유로 나이를 함께 둔다.
     */
    private final AtomicLong oldestPendingAgeSeconds = new AtomicLong();

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
        registry.gauge("openbake.ai.embedding.expired_lease", expiredLease);
        registry.gauge("openbake.ai.embedding.oldest_pending_age_seconds", oldestPendingAgeSeconds);
    }

    @Scheduled(
            fixedDelayString = "${openbake.ai.metrics.refresh-interval:PT30S}",
            initialDelayString = "${openbake.ai.metrics.refresh-interval:PT30S}")
    public void refresh() {
        taskCounts.forEach((status, count) -> count.set(taskRepository.countByStatus(status)));
        interactionRows.set(interactionRepository.count());

        Instant now = Instant.now();
        expiredLease.set(taskRepository.countExpiredLease(now));
        oldestPendingAgeSeconds.set(taskRepository.findOldestPendingCreatedAt()
                .map(createdAt -> Math.max(0, Duration.between(createdAt, now).toSeconds()))
                .orElse(0L));
    }
}
