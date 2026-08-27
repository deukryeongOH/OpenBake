package com.openbake.ai.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RecommendationServiceSizeTest {

    private final RecommendationService service = new RecommendationService(
            null, null, null, null, null, null, null, properties(), null);

    @Test
    void validatesSizeBoundariesAndDefault() {
        assertThrows(IllegalArgumentException.class, () -> service.validateSize(0));
        assertEquals(1, service.validateSize(1));
        assertEquals(20, service.validateSize(20));
        assertThrows(IllegalArgumentException.class, () -> service.validateSize(21));
        assertEquals(10, service.validateSize(null));
    }

    private RecommendationProperties properties() {
        return new RecommendationProperties(
                Duration.ofMinutes(15), 10, 20, 5, 100,
                Duration.ofDays(90), Duration.ofDays(30), Duration.ofDays(7),
                Duration.ofSeconds(3), 0.30, 3, 0.60, 0.30,
                new RecommendationProperties.Weights(1, 3, 5),
                new RecommendationProperties.HalfLife(
                        Duration.ofDays(5), Duration.ofDays(14), Duration.ofDays(45)));
    }
}
