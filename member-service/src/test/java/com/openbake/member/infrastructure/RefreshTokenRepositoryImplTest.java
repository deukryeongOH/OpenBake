package com.openbake.member.infrastructure;

import com.openbake.common.security.jwt.JwtProperties;
import com.openbake.member.infrastructure.security.jwt.RefreshTokenRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefreshTokenRepositoryImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RefreshTokenRepositoryImpl refreshTokenRepository;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties("test-secret", 1_800_000L, 1_209_600_000L);
        refreshTokenRepository = new RefreshTokenRepositoryImpl(redisTemplate, jwtProperties);
    }

    @Test
    @DisplayName("memberId를 키로 refreshToken을 TTL과 함께 저장한다")
    void save_storesWithMemberIdKeyAndTtl() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        refreshTokenRepository.save(1L, "refresh-token");

        verify(valueOperations).set("refresh:1", "refresh-token", Duration.ofMillis(1_209_600_000L));
    }

    @Test
    @DisplayName("memberId로 저장된 refreshToken을 조회한다")
    void findByMemberId_returnsStoredToken() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("refresh:1")).willReturn("refresh-token");

        Optional<String> result = refreshTokenRepository.findByMemberId(1L);

        assertThat(result).contains("refresh-token");
    }

    @Test
    @DisplayName("저장된 토큰이 없으면 빈 값을 반환한다")
    void findByMemberId_notFound_returnsEmpty() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("refresh:1")).willReturn(null);

        Optional<String> result = refreshTokenRepository.findByMemberId(1L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("memberId 기준으로 refreshToken을 삭제한다")
    void deleteByMemberId_deletesKey() {
        refreshTokenRepository.deleteByMemberId(1L);

        verify(redisTemplate).delete("refresh:1");
    }
}
