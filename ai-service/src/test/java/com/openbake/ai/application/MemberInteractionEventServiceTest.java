package com.openbake.ai.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openbake.ai.domain.ConsumedEventRepository;
import com.openbake.ai.domain.MemberProductInteraction;
import com.openbake.ai.infrastructure.jpa.MemberDeletionMarkerJpaRepository;
import com.openbake.ai.infrastructure.jpa.MemberProductInteractionJpaRepository;
import com.openbake.common.event.EventTopics;
import com.openbake.common.event.InteractionType;
import com.openbake.common.event.MemberInteractionEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class MemberInteractionEventServiceTest {

    @Mock
    private ConsumedEventRepository consumedEvents;
    @Mock
    private MemberDeletionMarkerJpaRepository markers;
    @Mock
    private MemberProductInteractionJpaRepository interactions;
    @Mock
    private ApplicationEventPublisher publisher;

    private MemberInteractionEventService service;

    @BeforeEach
    void setUp() {
        service = new MemberInteractionEventService(
                consumedEvents, markers, interactions, properties(), publisher);
    }

    @Test
    void duplicateStopsBeforeMarkerAndInteractionChecks() {
        MemberInteractionEvent event = view(Instant.now());
        when(consumedEvents.claim(any(), any(), any(Integer.class), any(Long.class), any(), any(), any()))
                .thenReturn(0);

        service.consume(event, EventTopics.PRODUCT_VIEWED, 0, 1L);

        verify(markers, never()).existsById(any());
        verify(interactions, never()).save(any());
    }

    @Test
    void deletionMarkerBlocksDelayedInteractionAfterClaim() {
        MemberInteractionEvent event = view(Instant.now());
        when(consumedEvents.claim(any(), any(), any(Integer.class), any(Long.class), any(), any(), any()))
                .thenReturn(1);
        when(markers.existsById(1L)).thenReturn(true);

        service.consume(event, EventTopics.PRODUCT_VIEWED, 0, 1L);

        verify(interactions, never()).save(any());
    }

    @Test
    void recentViewIsSuppressed() {
        Instant occurredAt = Instant.parse("2026-08-20T01:10:00Z");
        MemberInteractionEvent event = view(occurredAt);
        when(consumedEvents.claim(any(), any(), any(Integer.class), any(Long.class), any(), any(), any()))
                .thenReturn(1);
        when(interactions.existsByMemberIdAndProductIdAndInteractionTypeAndOccurredAtBetween(
                1L, 2L, InteractionType.VIEW, occurredAt.minus(Duration.ofMinutes(5)), occurredAt))
                .thenReturn(true);

        service.consume(event, EventTopics.PRODUCT_VIEWED, 0, 1L);

        verify(interactions, never()).save(any(MemberProductInteraction.class));
    }

    private MemberInteractionEvent view(Instant occurredAt) {
        return new MemberInteractionEvent(
                UUID.randomUUID(), 1, InteractionType.VIEW, occurredAt,
                1L, 2L, null, 1, null);
    }

    private InteractionProperties properties() {
        return new InteractionProperties(
                Duration.ofMinutes(5), Duration.ofDays(90), Duration.ofDays(90),
                Duration.ofDays(35), 1000, "recommendation:member:");
    }
}
