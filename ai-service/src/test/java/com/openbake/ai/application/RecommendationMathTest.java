package com.openbake.ai.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecommendationMathTest {

    @Test
    void halfLifeReducesDecayToExactlyOneHalf() {
        assertEquals(0.5, RecommendationMath.decay(Duration.ofDays(5), Duration.ofDays(5)), 1e-12);
    }

    @Test
    void groupedScoreUsesAverageDecayAndLog1pCompression() {
        double score = RecommendationMath.groupedScore(3.0, List.of(1.0, 0.5));

        assertEquals(3.0 * 0.75 * Math.log1p(2), score, 1e-12);
        assertTrue(score < 3.0 * 0.75 * 2);
    }

    @Test
    void minMaxNormalizationMapsRangeAndTreatsEqualScoresAsValid() {
        assertEquals(List.of(0.0, 0.5, 1.0),
                RecommendationMath.minMaxNormalize(List.of(2.0, 3.0, 4.0)));
        assertEquals(List.of(1.0, 1.0),
                RecommendationMath.minMaxNormalize(List.of(7.0, 7.0)));
    }
}
