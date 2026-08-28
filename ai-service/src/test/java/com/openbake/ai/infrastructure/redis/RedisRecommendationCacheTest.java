package com.openbake.ai.infrastructure.redis;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openbake.ai.application.InteractionProperties;
import com.openbake.ai.application.RecommendationCacheEntry;
import com.openbake.ai.application.RecommendationProperties;
import com.openbake.ai.domain.RecommendationStrategy;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

class RedisRecommendationCacheTest {

    @Test
    void redisReadAndWriteFailuresBehaveAsCacheMisses() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForValue()).thenThrow(
                new RedisConnectionFailureException("unavailable"));
        RedisRecommendationCache cache = new RedisRecommendationCache(
                redisTemplate,
                JsonMapper.builder().build(),
                new InteractionProperties(
                        Duration.ofMinutes(5), Duration.ofDays(90), Duration.ofDays(90),
                        Duration.ofDays(35), 1000, "ai:recommendation:v1:"),
                properties());

        assertTrue(cache.find(1L).isEmpty());
        assertDoesNotThrow(() -> cache.save(1L,
                new RecommendationCacheEntry(RecommendationStrategy.LATEST, List.of())));
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
