package com.openbake.ai.infrastructure.redis;

import com.openbake.ai.application.InteractionProperties;
import com.openbake.ai.application.RecommendationCacheInvalidationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class RecommendationCacheInvalidator {

    private final StringRedisTemplate redisTemplate;
    private final InteractionProperties properties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void invalidate(RecommendationCacheInvalidationEvent event) {
        String key = properties.recommendationCacheKeyPrefix() + event.memberId();
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException exception) {
            log.warn("추천 캐시 삭제 실패 memberId={} key={}", event.memberId(), key, exception);
        }
    }
}
