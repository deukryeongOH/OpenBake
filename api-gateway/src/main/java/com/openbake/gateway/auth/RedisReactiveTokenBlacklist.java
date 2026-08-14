package com.openbake.gateway.auth;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Component
public class RedisReactiveTokenBlacklist
        implements ReactiveTokenBlacklist {

    static final String BLACKLIST_KEY_PREFIX = "blacklist:";

    private final ReactiveStringRedisTemplate redisTemplate;

    public RedisReactiveTokenBlacklist(
            ReactiveStringRedisTemplate redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Boolean> contains(String rawAccessToken) {
        Objects.requireNonNull(
                rawAccessToken,
                "rawAccessToken must not be null"
        );

        if (rawAccessToken.isBlank()) {
            return Mono.error(new IllegalArgumentException(
                    "rawAccessToken must not be blank"
            ));
        }

        return redisTemplate.hasKey(
                BLACKLIST_KEY_PREFIX + rawAccessToken
        );
    }
}