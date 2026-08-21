package com.openbake.ai.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openbake.ai.domain.ConsumedEventRepository;
import com.openbake.ai.infrastructure.jpa.MemberDeletionMarkerJpaRepository;
import com.openbake.ai.infrastructure.jpa.MemberProductInteractionJpaRepository;
import com.openbake.common.event.EventTopics;
import com.openbake.common.event.MemberWithdrawnEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class MemberWithdrawnEventServiceTest {

    @Mock
    private ConsumedEventRepository consumedEvents;
    @Mock
    private MemberProductInteractionJpaRepository interactions;
    @Mock
    private MemberDeletionMarkerJpaRepository markers;
    @Mock
    private ApplicationEventPublisher publisher;

    @Test
    void hardDeletesAndSetsThirtyFiveDayMarker() {
        InteractionProperties properties = new InteractionProperties(
                Duration.ofMinutes(5), Duration.ofDays(90), Duration.ofDays(90),
                Duration.ofDays(35), 1000, "recommendation:member:");
        MemberWithdrawnEventService service = new MemberWithdrawnEventService(
                consumedEvents, interactions, markers, properties, publisher);
        Instant withdrawnAt = Instant.parse("2026-08-20T01:00:00Z");
        MemberWithdrawnEvent event = new MemberWithdrawnEvent(
                UUID.randomUUID(), 1, withdrawnAt, 7L, withdrawnAt);
        when(consumedEvents.claim(any(), any(), any(Integer.class), any(Long.class), any(), any(), any()))
                .thenReturn(1);
        when(interactions.hardDeleteByMemberId(7L)).thenReturn(2);
        when(markers.upsertLatest(any(), any(), any(), any())).thenReturn(1);

        service.consume(event, EventTopics.MEMBER_WITHDRAWN, 0, 1L);

        verify(interactions).hardDeleteByMemberId(7L);
        verify(markers).upsertLatest(
                7L, event.eventId(), withdrawnAt, withdrawnAt.plus(Duration.ofDays(35)));
        verify(publisher).publishEvent(new RecommendationCacheInvalidationEvent(7L));
    }
}
