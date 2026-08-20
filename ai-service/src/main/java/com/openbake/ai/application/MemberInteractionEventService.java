package com.openbake.ai.application;

import com.openbake.ai.domain.ConsumedEventRepository;
import com.openbake.ai.domain.MemberProductInteraction;
import com.openbake.ai.infrastructure.jpa.MemberDeletionMarkerJpaRepository;
import com.openbake.ai.infrastructure.jpa.MemberProductInteractionJpaRepository;
import com.openbake.common.event.InteractionType;
import com.openbake.common.event.MemberInteractionEvent;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberInteractionEventService {

    private final ConsumedEventRepository consumedEventRepository;
    private final MemberDeletionMarkerJpaRepository deletionMarkerRepository;
    private final MemberProductInteractionJpaRepository interactionRepository;
    private final InteractionProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void consume(MemberInteractionEvent event, String topic, int partition, long offset) {
        event.validateForTopic(topic);
        Instant consumedAt = Instant.now();
        if (consumedEventRepository.claim(
                event.eventId(), topic, partition, offset, event.interactionType().name(),
                event.occurredAt(), consumedAt) == 0) {
            return;
        }

        if (deletionMarkerRepository.existsById(event.memberId())) {
            return;
        }

        if (event.interactionType() == InteractionType.VIEW
                && interactionRepository
                .existsByMemberIdAndProductIdAndInteractionTypeAndOccurredAtBetween(
                        event.memberId(), event.productId(), InteractionType.VIEW,
                        event.occurredAt().minus(properties.viewSuppression()), event.occurredAt())) {
            return;
        }

        interactionRepository.save(MemberProductInteraction.from(event, consumedAt));
        eventPublisher.publishEvent(new RecommendationCacheInvalidationEvent(event.memberId()));
    }
}
