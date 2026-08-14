package com.openbake.member.infrastructure;

import com.openbake.member.infrastructure.security.jwt.AccessTokenRepositoryImpl;
import com.openbake.member.infrastructure.security.jwt.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccessTokenRepositoryImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private AccessTokenRepositoryImpl accessTokenRepository;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties("test-secret", 1_800_000L, 1_209_600_000L);
        accessTokenRepository = new AccessTokenRepositoryImpl(redisTemplate, jwtProperties);
    }

    @Test
    @DisplayName("memberId를 키로 accessToken을 TTL과 함께 저장한다")
    void save_storesWithMemberIdKeyAndTtl() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        accessTokenRepository.save(1L, "access-token");

        verify(valueOperations).set("access:1", "access-token", Duration.ofMillis(1_800_000L));
    }

    @Test
    @DisplayName("memberId로 저장된 accessToken을 블랙리스트로 옮기고 access 키는 삭제한다")
    void blacklistByMemberId_movesTokenToBlacklistAndDeletesAccessKey() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("access:1")).willReturn("access-token");

        accessTokenRepository.blacklistByMemberId(1L);

        verify(valueOperations).set("blacklist:access-token", "1", Duration.ofMillis(1_800_000L));
        verify(redisTemplate).delete("access:1");
    }

    @Test
    @DisplayName("저장된 accessToken이 없으면 아무 것도 하지 않는다")
    void blacklistByMemberId_noStoredToken_doesNothing() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("access:1")).willReturn(null);

        accessTokenRepository.blacklistByMemberId(1L);

        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("블랙리스트 키가 있으면 true를 반환한다")
    void isBlacklisted_keyExists_returnsTrue() {
        given(redisTemplate.hasKey("blacklist:access-token")).willReturn(true);

        assertThat(accessTokenRepository.isBlacklisted("access-token")).isTrue();
    }

    @Test
    @DisplayName("블랙리스트 키가 없으면 false를 반환한다")
    void isBlacklisted_keyNotExists_returnsFalse() {
        given(redisTemplate.hasKey("blacklist:access-token")).willReturn(false);

        assertThat(accessTokenRepository.isBlacklisted("access-token")).isFalse();
    }
}
