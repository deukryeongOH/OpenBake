package com.openbake.ai.infrastructure.jpa;

import com.openbake.ai.domain.ConsumedEvent;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConsumedEventJpaRepository extends JpaRepository<ConsumedEvent, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO consumed_events
                (event_id, topic, partition_id, topic_offset, event_type, occurred_at, consumed_at)
            VALUES
                (:eventId, :topic, :partitionId, :topicOffset, :eventType, :occurredAt, :consumedAt)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int claim(
            @Param("eventId") UUID eventId,
            @Param("topic") String topic,
            @Param("partitionId") int partitionId,
            @Param("topicOffset") long topicOffset,
            @Param("eventType") String eventType,
            @Param("occurredAt") Instant occurredAt,
            @Param("consumedAt") Instant consumedAt);
}
