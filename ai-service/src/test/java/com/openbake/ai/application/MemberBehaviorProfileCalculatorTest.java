package com.openbake.ai.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.openbake.ai.domain.MemberProductInteraction;
import com.openbake.common.event.InteractionType;
import com.openbake.common.event.MemberInteractionEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MemberBehaviorProfileCalculatorTest {

    private final MemberBehaviorProfileCalculator calculator =
            new MemberBehaviorProfileCalculator(properties());

    @Test
    void countsOriginalValidLogsAndDoesNotMultiplyProfileByQuantity() {
        Instant now = Instant.parse("2026-08-20T00:00:00Z");
        Map<Long, ProductEmbeddingSnapshot> documents = Map.of(
                1L, new ProductEmbeddingSnapshot(1L, "BREAD", "GENERAL", List.of(1f, 0f), 0),
                2L, new ProductEmbeddingSnapshot(2L, "CAKE", "GENERAL", List.of(0f, 1f), 0));

        RecommendationProfile lowQuantity = calculator.calculate(List.of(
                interaction(1L, InteractionType.PURCHASE, 1, now.minus(Duration.ofDays(1))),
                interaction(1L, InteractionType.PURCHASE, 1, now.minus(Duration.ofDays(2))),
                interaction(2L, InteractionType.VIEW, 1, now.minus(Duration.ofDays(1)))), documents, now)
                .orElseThrow();
        RecommendationProfile highQuantity = calculator.calculate(List.of(
                interaction(1L, InteractionType.PURCHASE, 100, now.minus(Duration.ofDays(1))),
                interaction(1L, InteractionType.PURCHASE, 200, now.minus(Duration.ofDays(2))),
                interaction(2L, InteractionType.VIEW, 50, now.minus(Duration.ofDays(1)))), documents, now)
                .orElseThrow();

        assertEquals(3, lowQuantity.validInteractionCount());
        assertEquals(lowQuantity.categoryAffinity(), highQuantity.categoryAffinity());
        assertEquals(lowQuantity.interestVector(), highQuantity.interestVector());
    }

    @Test
    void ignoresLogsWhoseDocumentOrVectorIsMissing() {
        Instant now = Instant.now();
        Map<Long, ProductEmbeddingSnapshot> documents = Map.of(
                1L, new ProductEmbeddingSnapshot(1L, "BREAD", "GENERAL", List.of(1f), 0),
                2L, new ProductEmbeddingSnapshot(2L, "CAKE", "GENERAL", List.of(), 0));

        RecommendationProfile profile = calculator.calculate(List.of(
                interaction(1L, InteractionType.VIEW, 1, now),
                interaction(2L, InteractionType.VIEW, 1, now),
                interaction(3L, InteractionType.VIEW, 1, now)), documents, now).orElseThrow();

        assertEquals(1, profile.validInteractionCount());
    }

    private MemberProductInteraction interaction(
            Long productId, InteractionType type, int quantity, Instant occurredAt) {
        return MemberProductInteraction.from(new MemberInteractionEvent(
                UUID.randomUUID(), 1, type, occurredAt, 10L, productId, null,
                quantity, type == InteractionType.PURCHASE ? 1L : null), occurredAt);
    }

    private RecommendationProperties properties() {
        return new RecommendationProperties(
                Duration.ofMinutes(15), 10, 20, 5, 100,
                Duration.ofDays(90), Duration.ofDays(30), Duration.ofDays(7),
                Duration.ofSeconds(3),
                new RecommendationProperties.Weights(1, 3, 5),
                new RecommendationProperties.HalfLife(
                        Duration.ofDays(5), Duration.ofDays(14), Duration.ofDays(45)));
    }
}
