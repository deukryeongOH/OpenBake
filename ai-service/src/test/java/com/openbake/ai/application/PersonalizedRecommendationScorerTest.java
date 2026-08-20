package com.openbake.ai.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openbake.ai.domain.RecommendationReason;
import com.openbake.common.event.InteractionType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PersonalizedRecommendationScorerTest {

    private final PersonalizedRecommendationScorer scorer = new PersonalizedRecommendationScorer();

    @Test
    void validInteractionBoundarySwitchesFromSixtyFortyToThirtySeventy() {
        ProductEmbeddingSnapshot candidate = candidate(1L, "BREAD", 0.0, List.of(1f, 0f));

        RecommendationCandidate sparse = scorer.score(profile(2), List.of(candidate)).getFirst();
        RecommendationCandidate established = scorer.score(profile(3), List.of(candidate)).getFirst();

        assertEquals(1.0, sparse.score(), 1e-12);
        assertEquals(1.0, established.score(), 1e-12);

        ProductEmbeddingSnapshot otherCategory = candidate(2L, "CAKE", 0.0, List.of(1f, 0f));
        assertEquals(0.4, scorer.score(profile(2), List.of(otherCategory)).getFirst().score(), 1e-12);
        assertEquals(0.7, scorer.score(profile(3), List.of(otherCategory)).getFirst().score(), 1e-12);
    }

    @Test
    void categoryDominanceAndStrongestInteractionChooseReasonCodes() {
        RecommendationProfile categoryDominated = new RecommendationProfile(
                Map.of("BREAD", 1.0), List.of(1f, 0f),
                Map.of(InteractionType.PURCHASE, List.of(1f, 0f)),
                Map.of(InteractionType.PURCHASE, 5.0), 2);
        List<ProductEmbeddingSnapshot> twoCandidates = List.of(
                candidate(1L, "BREAD", 0.1, List.of(1f, 0f)),
                candidate(2L, "CAKE", 0.9, List.of(1f, 0f)));

        Map<Long, RecommendationReason> reasons = scorer.score(categoryDominated, twoCandidates).stream()
                .collect(java.util.stream.Collectors.toMap(
                        RecommendationCandidate::productId, RecommendationCandidate::reasonCode));

        assertEquals(RecommendationReason.PREFERRED_CATEGORY, reasons.get(1L));
        assertEquals(RecommendationReason.SIMILAR_TO_PURCHASED, reasons.get(2L));
        assertTrue(scorer.score(categoryDominated, twoCandidates).stream()
                .allMatch(candidate -> candidate.score() >= 0.0 && candidate.score() <= 1.0));
    }

    private RecommendationProfile profile(int validCount) {
        return new RecommendationProfile(
                Map.of("BREAD", 1.0), List.of(1f, 0f),
                Map.of(InteractionType.VIEW, List.of(1f, 0f)),
                Map.of(InteractionType.VIEW, 1.0), validCount);
    }

    private ProductEmbeddingSnapshot candidate(
            Long id, String category, double score, List<Float> vector) {
        return new ProductEmbeddingSnapshot(id, category, "GENERAL", vector, score);
    }
}
