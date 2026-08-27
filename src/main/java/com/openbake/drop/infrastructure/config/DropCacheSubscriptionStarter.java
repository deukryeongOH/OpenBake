package com.openbake.drop.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * {@link DropCacheMessagingConfig}에서 autoStartup을 꺼 둔 구독을 대신 시작한다.
 *
 * 컨텍스트가 완전히 뜬 뒤(ApplicationReadyEvent)에 시작을 시도하고, 실패하면 예외를 삼켜
 * 로그만 남긴다 — 여기서 예외가 올라가면 리스너 체인이 끊길 수 있다. Redis가 앱 기동
 * 시점에 아직 안 떠 있었던 경우를 대비해, 구독이 안 걸려 있으면(isRunning() == false)
 * 30초마다 재시도한다. 한 번 성공적으로 구독이 걸리면 그 뒤의 연결 끊김·재연결은
 * RedisMessageListenerContainer 자체의 recoveryInterval이 처리하므로 이 재시도는
 * 최초 연결 실패 케이스만 커버하면 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DropCacheSubscriptionStarter {

    private final RedisMessageListenerContainer dropCacheMessageListenerContainer;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        tryStart();
    }

    @Scheduled(fixedDelay = 30000)
    public void ensureSubscribed() {
        if (!dropCacheMessageListenerContainer.isRunning()) {
            tryStart();
        }
    }

    private void tryStart() {
        try {
            dropCacheMessageListenerContainer.start();
        } catch (Exception e) {
            log.warn("[드롭 캐시] 무효화 신호 구독 시작 실패 — 다음 재시도(최대 30초 뒤)에서 다시 시도합니다. reason={}", e.getMessage());
        }
    }
}