package com.openbake.member.infrastructure;

import com.openbake.member.domain.RefreshTokenRepository;
import com.openbake.member.infrastructure.jwt.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepositoryImpl implements RefreshTokenRepository {

    private static final String KEY_PREFIX = "refresh:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    @Override
    public void save(Long memberId, String refreshToken) {
        Duration ttl = Duration.ofMillis(jwtProperties.refreshTokenExpiration());
        redisTemplate.opsForValue().set(key(memberId), refreshToken, ttl);
    }

    @Override
    public Optional<String> findByMemberId(Long memberId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(memberId)));
    }

    @Override
    public void deleteByMemberId(Long memberId) {
        redisTemplate.delete(key(memberId));
    }

    private String key(Long memberId) {
        return KEY_PREFIX + memberId;
    }
}
