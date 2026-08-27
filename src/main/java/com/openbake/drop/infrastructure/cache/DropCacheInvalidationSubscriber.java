package com.openbake.drop.infrastructure.cache;

import com.openbake.drop.application.cache.TodayDropCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * 다른 Pod가 보낸 무효화 신호를 받아 이 Pod의 TodayDropCache를 다시 읽는다.
 *
 * 신호를 보낸 Pod 자신도 같은 채널을 구독하므로 한 번 더 refresh하게 되지만,
 * 하루 드롭이 최대 TimeSlot 수(5개)라 중복 조회 비용이 무시할 수준이다.
 * 자기 자신을 제외하는 로직은 두지 않는다(docs/11-drop-cache-invalidation-propagation.md 4장).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DropCacheInvalidationSubscriber implements MessageListener {

    private final TodayDropCache todayDropCache;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        log.debug("[드롭 캐시] 무효화 신호 수신 — 재갱신");
        todayDropCache.refresh();
    }
}