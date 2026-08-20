package com.openbake.ai.infrastructure.redis;

import com.openbake.ai.application.InteractionProperties;
import com.openbake.ai.application.RecommendationCacheEntry;
import com.openbake.ai.application.RecommendationProperties;
import com.openbake.ai.application.port.RecommendationCache;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisRecommendationCache implements RecommendationCache {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final InteractionProperties interactionProperties;
    private final RecommendationProperties recommendationProperties;

    @Override
    public Optional<RecommendationCacheEntry> find(Long memberId) {
        String key = key(memberId);
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(value, RecommendationCacheEntry.class));
        } catch (RuntimeException exception) {
            log.warn("추천 캐시 조회 실패 memberId={} key={}", memberId, key, exception);
            return Optional.empty();
        }
    }

    @Override
    public void save(Long memberId, RecommendationCacheEntry entry) {
        String key = key(memberId);
        try {
            redisTemplate.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(entry),
                    recommendationProperties.cacheTtl());
        } catch (RuntimeException exception) {
            log.warn("추천 캐시 저장 실패 memberId={} key={}", memberId, key, exception);
        }
    }

    private String key(Long memberId) {
        return interactionProperties.recommendationCacheKeyPrefix() + memberId;
    }
}
