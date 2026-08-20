package com.openbake.ai.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.openbake.ai.domain.MemberProductInteraction;
import com.openbake.common.event.InteractionType;
import com.openbake.common.event.MemberInteractionEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PopularRecommendationCalculatorTest {

    private final PopularRecommendationCalculator calculator = new PopularRecommendationCalculator();

    @Test
    void sumsPurchaseQuantityAndCartEventsExcludingDropAndNonGeneralWithCategoryCap() {
        List<MemberProductInteraction> signals = List.of(
                interaction(1L, InteractionType.PURCHASE, 3, null),
                interaction(1L, InteractionType.CART_ADD, 99, null),
                interaction(2L, InteractionType.PURCHASE, 4, null),
                interaction(3L, InteractionType.PURCHASE, 100, 30L),
                interaction(4L, InteractionType.PURCHASE, 100, null),
                interaction(5L, InteractionType.PURCHASE, 4, null));
        Map<Long, ProductEmbeddingSnapshot> documents = Map.of(
                1L, document(1L, "BREAD", "GENERAL"),
                2L, document(2L, "BREAD", "GENERAL"),
                3L, document(3L, "CAKE", "GENERAL"),
                4L, document(4L, "CAKE", "DROP"),
                5L, document(5L, "CAKE", "GENERAL"));

        List<RecommendationCandidate> result = calculator.calculate(signals, documents, 2, 10);

        assertEquals(List.of(5L, 2L), result.stream()
                .map(RecommendationCandidate::productId).toList());
        assertEquals(List.of(4.0, 4.0), result.stream()
                .map(RecommendationCandidate::score).toList());
    }

    private MemberProductInteraction interaction(
            Long productId, InteractionType type, int quantity, Long dropId) {
        Instant now = Instant.now();
        return MemberProductInteraction.from(new MemberInteractionEvent(
                UUID.randomUUID(), 1, type, now, 10L, productId,
                dropId, quantity, type == InteractionType.PURCHASE ? 1L : null), now);
    }

    private ProductEmbeddingSnapshot document(Long id, String category, String type) {
        return new ProductEmbeddingSnapshot(id, category, type, List.of(1f), 0.0);
    }
}
