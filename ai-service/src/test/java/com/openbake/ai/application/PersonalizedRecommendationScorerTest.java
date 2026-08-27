package com.openbake.ai.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openbake.ai.domain.RecommendationReason;
import com.openbake.common.event.InteractionType;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PersonalizedRecommendationScorerTest {

    private final PersonalizedRecommendationScorer scorer =
            new PersonalizedRecommendationScorer(properties(0.30, 3, 0.60, 0.30));

    @Test
    void validInteractionBoundarySwitchesFromSixtyFortyToThirtySeventy() {
        ProductEmbeddingSnapshot candidate = candidate(1L, "BREAD", 0.8, List.of(1f, 0f));

        RecommendationCandidate sparse = scorer.score(profile(2), List.of(candidate)).getFirst();
        RecommendationCandidate established = scorer.score(profile(3), List.of(candidate)).getFirst();

        assertEquals(1.0, sparse.score(), 1e-12);
        assertEquals(1.0, established.score(), 1e-12);

        ProductEmbeddingSnapshot otherCategory = candidate(2L, "CAKE", 0.8, List.of(1f, 0f));
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
                candidate(1L, "BREAD", 0.7, List.of(1f, 0f)),
                candidate(2L, "CAKE", 0.9, List.of(1f, 0f)));

        Map<Long, RecommendationReason> reasons = scorer.score(categoryDominated, twoCandidates).stream()
                .collect(java.util.stream.Collectors.toMap(
                        RecommendationCandidate::productId, RecommendationCandidate::reasonCode));

        assertEquals(RecommendationReason.PREFERRED_CATEGORY, reasons.get(1L));
        assertEquals(RecommendationReason.SIMILAR_TO_PURCHASED, reasons.get(2L));
        assertTrue(scorer.score(categoryDominated, twoCandidates).stream()
                .allMatch(candidate -> candidate.score() >= 0.0 && candidate.score() <= 1.0));
    }

    @Test
    void filtersOnRawSimilarityBeforeMinMaxNormalization() {
        List<ProductEmbeddingSnapshot> weakCandidates = List.of(
                candidate(1L, "BREAD", 0.60, List.of(1f, 0f)),
                candidate(2L, "CAKE", 0.55, List.of(1f, 0f)));

        assertTrue(scorer.score(profile(3), weakCandidates).isEmpty());
    }

    @Test
    void zeroFloorRestoresUnfilteredScoring() {
        PersonalizedRecommendationScorer rollbackScorer =
                new PersonalizedRecommendationScorer(properties(0.0, 3, 0.60, 0.30));

        assertEquals(2, rollbackScorer.score(profile(3), List.of(
                candidate(1L, "BREAD", 0.10, List.of(1f, 0f)),
                candidate(2L, "CAKE", 0.05, List.of(1f, 0f)))).size());
    }

    @Test
    void categoryWeightsAndColdStartThresholdComeFromConfiguration() {
        PersonalizedRecommendationScorer configured =
                new PersonalizedRecommendationScorer(properties(0.0, 5, 0.80, 0.20));
        ProductEmbeddingSnapshot otherCategory = candidate(1L, "CAKE", 0.8, List.of(1f, 0f));

        assertEquals(0.20, configured.score(profile(4), List.of(otherCategory)).getFirst().score(), 1e-12);
        assertEquals(0.80, configured.score(profile(5), List.of(otherCategory)).getFirst().score(), 1e-12);
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

    private static RecommendationProperties properties(
            double minCosine,
            int coldStartThreshold,
            double coldStartCategoryWeight,
            double establishedCategoryWeight) {
        return new RecommendationProperties(
                Duration.ofMinutes(15), 10, 20, 5, 100,
                Duration.ofDays(90), Duration.ofDays(30), Duration.ofDays(7),
                Duration.ofSeconds(3), minCosine, coldStartThreshold,
                coldStartCategoryWeight, establishedCategoryWeight,
                new RecommendationProperties.Weights(1, 3, 5),
                new RecommendationProperties.HalfLife(
                        Duration.ofDays(5), Duration.ofDays(14), Duration.ofDays(45)));
    }
}
