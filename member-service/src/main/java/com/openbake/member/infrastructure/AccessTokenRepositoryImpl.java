package com.openbake.member.infrastructure;

import com.openbake.member.domain.AccessTokenRepository;
import com.openbake.member.infrastructure.jwt.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
@RequiredArgsConstructor
public class AccessTokenRepositoryImpl implements AccessTokenRepository {

    private static final String ACCESS_KEY_PREFIX = "access:";
    private static final String BLACKLIST_KEY_PREFIX = "blacklist:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    @Override
    public void save(Long memberId, String accessToken) {
        Duration ttl = Duration.ofMillis(jwtProperties.accessTokenExpiration());
        redisTemplate.opsForValue().set(accessKey(memberId), accessToken, ttl);
    }

    @Override
    public void blacklistByMemberId(Long memberId) {
        String accessToken = redisTemplate.opsForValue().get(accessKey(memberId));
        if (accessToken == null) {
            return;
        }

        // 토큰 발급 시각을 안 들고 있어서 남은 만료시간을 정확히는 못 구함 — accessTokenExpiration 전체로
        // TTL을 잡아 실제 만료보다 항상 길게(느슨하게) 잡히도록 함
        Duration ttl = Duration.ofMillis(jwtProperties.accessTokenExpiration());
        redisTemplate.opsForValue().set(blacklistKey(accessToken), "1", ttl);
        redisTemplate.delete(accessKey(memberId));
    }

    @Override
    public boolean isBlacklisted(String accessToken) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey(accessToken)));
    }

    private String accessKey(Long memberId) {
        return ACCESS_KEY_PREFIX + memberId;
    }

    private String blacklistKey(String accessToken) {
        return BLACKLIST_KEY_PREFIX + accessToken;
    }
}
