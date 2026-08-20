package com.openbake.ai.application;

import com.openbake.ai.domain.ConsumedEventRepository;
import com.openbake.ai.infrastructure.jpa.MemberDeletionMarkerJpaRepository;
import com.openbake.ai.infrastructure.jpa.MemberProductInteractionJpaRepository;
import com.openbake.common.event.MemberWithdrawnEvent;
import com.openbake.common.event.EventTopics;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberWithdrawnEventService {

    private final ConsumedEventRepository consumedEventRepository;
    private final MemberProductInteractionJpaRepository interactionRepository;
    private final MemberDeletionMarkerJpaRepository deletionMarkerRepository;
    private final InteractionProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void consume(MemberWithdrawnEvent event, String topic, int partition, long offset) {
        event.validate();
        if (!EventTopics.MEMBER_WITHDRAWN.equals(topic)) {
            throw new IllegalArgumentException("unsupported member withdrawal topic: " + topic);
        }
        Instant consumedAt = Instant.now();
        if (consumedEventRepository.claim(
                event.eventId(), topic, partition, offset, "WITHDRAWN",
                event.occurredAt(), consumedAt) == 0) {
            return;
        }

        int deleted = interactionRepository.hardDeleteByMemberId(event.memberId());
        int markerChanged = deletionMarkerRepository.upsertLatest(
                event.memberId(), event.eventId(), event.withdrawnAt(),
                event.withdrawnAt().plus(properties.deletionMarkerRetention()));

        if (deleted > 0 || markerChanged > 0) {
            eventPublisher.publishEvent(new RecommendationCacheInvalidationEvent(event.memberId()));
        }
    }
}
