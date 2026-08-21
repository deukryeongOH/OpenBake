package com.openbake.ai.infrastructure.jpa;

import com.openbake.ai.domain.ConsumedEventRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ConsumedEventRepositoryAdapter implements ConsumedEventRepository {

    private final ConsumedEventJpaRepository jpaRepository;

    @Override
    public boolean existsById(UUID eventId) {
        return jpaRepository.existsById(eventId);
    }

    @Override
    public int claim(
            UUID eventId,
            String topic,
            int partitionId,
            long topicOffset,
            String eventType,
            Instant occurredAt,
            Instant consumedAt) {
        return jpaRepository.claim(
                eventId, topic, partitionId, topicOffset, eventType, occurredAt, consumedAt);
    }
}
