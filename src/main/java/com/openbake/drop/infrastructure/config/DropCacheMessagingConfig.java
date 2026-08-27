package com.openbake.drop.infrastructure.config;

import com.openbake.drop.infrastructure.cache.DropCacheInvalidationPublisher;
import com.openbake.drop.infrastructure.cache.DropCacheInvalidationSubscriber;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 드롭 캐시 무효화 채널(drop:cache:refresh) 구독을 설정한다.
 * RedisStockReservationAdapter와 같은 Redis 연결(spring.data.redis.*)을 공유하며 새 인프라는 없다.
 *
 * autoStartup을 꺼 둔다. RedisMessageListenerContainer는 SmartLifecycle이라 기본값(true)이면
 * 컨텍스트 기동(refresh) 중에 Spring이 직접 start()를 호출하는데, 그 시점에 Redis가 아직
 * 떠 있지 않으면 연결 예외가 컨텍스트 기동 자체를 실패시킨다 — 드롭 캐시와 전혀 무관한
 * 다른 기능까지 앱 전체가 못 뜨게 된다. 실제로 로컬 Redis 없이 전체 컨텍스트를 올리는
 * 다른 통합 테스트들에서 이 문제가 발생했다. 시작을 컨텍스트 기동과 분리해
 * {@link DropCacheSubscriptionStarter}가 대신 담당하게 한다.
 */
@Configuration
@RequiredArgsConstructor
public class DropCacheMessagingConfig {

    private final RedisConnectionFactory redisConnectionFactory;
    private final DropCacheInvalidationSubscriber dropCacheInvalidationSubscriber;

    @Bean
    public RedisMessageListenerContainer dropCacheMessageListenerContainer() {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(
                dropCacheInvalidationSubscriber,
                new ChannelTopic(DropCacheInvalidationPublisher.CHANNEL));
        container.setAutoStartup(false);
        return container;
    }
}