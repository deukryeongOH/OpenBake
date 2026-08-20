package com.openbake.ai.domain;

import java.time.Instant;
import java.util.UUID;

public interface ConsumedEventRepository {

    int claim(
            UUID eventId,
            String topic,
            int partitionId,
            long topicOffset,
            String eventType,
            Instant occurredAt,
            Instant consumedAt);
}
