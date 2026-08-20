package com.openbake.ai.infrastructure.scheduler;

import com.openbake.ai.application.InteractionProperties;
import com.openbake.ai.infrastructure.jpa.ConsumedEventJpaRepository;
import com.openbake.ai.infrastructure.jpa.MemberDeletionMarkerJpaRepository;
import com.openbake.ai.infrastructure.jpa.MemberProductInteractionJpaRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class InteractionRetentionScheduler {

    private final MemberProductInteractionJpaRepository interactionRepository;
    private final ConsumedEventJpaRepository consumedEventRepository;
    private final MemberDeletionMarkerJpaRepository deletionMarkerRepository;
    private final InteractionProperties properties;

    @Scheduled(cron = "${openbake.ai.interaction.cleanup-cron:0 20 4 * * *}")
    @Transactional
    public void clean() {
        Instant now = Instant.now();
        int batchSize = properties.cleanupBatchSize();
        int interactions = interactionRepository.deleteBatchBefore(
                now.minus(properties.interactionRetention()), batchSize);
        int consumed = consumedEventRepository.deleteBatchBefore(
                now.minus(properties.consumedEventRetention()), batchSize);
        int markers = deletionMarkerRepository.deleteExpiredBatch(now, batchSize);
        if (interactions + consumed + markers > 0) {
            log.info("AI 보관 데이터 정리 interactions={} consumedEvents={} deletionMarkers={}",
                    interactions, consumed, markers);
        }
    }
}
