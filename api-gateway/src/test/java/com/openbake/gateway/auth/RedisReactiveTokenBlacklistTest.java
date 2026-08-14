package com.openbake.gateway.auth;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisReactiveTokenBlacklistTest {

    private static final String TOKEN = "test-access-token";

    private final ReactiveStringRedisTemplate redisTemplate =
            mock(ReactiveStringRedisTemplate.class);

    private final RedisReactiveTokenBlacklist blacklist =
            new RedisReactiveTokenBlacklist(redisTemplate);

    @Test
    void looksUpExistingBlacklistKey() {
        when(redisTemplate.hasKey("blacklist:" + TOKEN))
                .thenReturn(Mono.just(true));

        StepVerifier.create(blacklist.contains(TOKEN))
                .expectNext(true)
                .verifyComplete();

        verify(redisTemplate)
                .hasKey("blacklist:" + TOKEN);
    }

    @Test
    void returnsFalseWhenBlacklistKeyDoesNotExist() {
        when(redisTemplate.hasKey("blacklist:" + TOKEN))
                .thenReturn(Mono.just(false));

        StepVerifier.create(blacklist.contains(TOKEN))
                .expectNext(false)
                .verifyComplete();

        verify(redisTemplate)
                .hasKey("blacklist:" + TOKEN);
    }

    @Test
    void propagatesRedisFailure() {
        RedisConnectionFailureException failure =
                new RedisConnectionFailureException(
                        "Redis unavailable"
                );

        when(redisTemplate.hasKey("blacklist:" + TOKEN))
                .thenReturn(Mono.error(failure));

        StepVerifier.create(blacklist.contains(TOKEN))
                .expectErrorSatisfies(error ->
                        org.junit.jupiter.api.Assertions.assertSame(
                                failure,
                                error
                        )
                )
                .verify();
    }

    @Test
    void rejectsBlankTokenWithoutCallingRedis() {
        StepVerifier.create(blacklist.contains(" "))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void rejectsNullTokenWithoutCallingRedis() {
        org.junit.jupiter.api.Assertions.assertThrows(
                NullPointerException.class,
                () -> blacklist.contains(null)
        );
    }
}