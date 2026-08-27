package com.openbake.drop.infrastructure.cache;

import com.openbake.drop.application.cache.DropCacheInvalidatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 다른 Pod에게 "오늘 드롭이 바뀌었으니 캐시를 다시 읽어라" 신호만 보낸다. 값 자체는 옮기지 않는다.
 *
 * AFTER_COMMIT에서만 발행한다. DropService의 등록/수정/삭제 메서드는 @Transactional이고
 * 로컬 todayDropCache.refresh()는 커밋 전(같은 트랜잭션·같은 커넥션)에 호출돼도 안전하지만,
 * 다른 Pod는 다른 커넥션으로 조회하므로 커밋 전에 신호를 받으면 READ COMMITTED 하에서
 * 여전히 이전 상태를 읽는다(docs/11-drop-cache-invalidation-propagation.md 3.1절).
 */
@Component
@RequiredArgsConstructor
public class DropCacheInvalidationPublisher {

    public static final String CHANNEL = "drop:cache:refresh";

    private final StringRedisTemplate redisTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDropCacheInvalidated(DropCacheInvalidatedEvent event) {
        redisTemplate.convertAndSend(CHANNEL, "refresh");
    }
}