package com.openbake.member.infrastructure.jwt;

import com.openbake.member.domain.Role;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(
                "test-secret-key-for-jwt-token-provider-unit-test-32bytes+",
                1_800_000L,
                1_209_600_000L);
        jwtTokenProvider = new JwtTokenProvider(properties);
    }

    @Test
    @DisplayName("Access Token은 memberId와 role 클레임을 담아 발급된다")
    void createAccessToken_containsMemberIdAndRole() {
        String token = jwtTokenProvider.createAccessToken(1L, Role.CUSTOMER);

        assertThat(jwtTokenProvider.getMemberId(token)).isEqualTo(1L);
        assertThat(jwtTokenProvider.getRole(token)).isEqualTo(Role.CUSTOMER);
        assertThat(jwtTokenProvider.isValid(token)).isTrue();
    }

    @Test
    @DisplayName("Refresh Token은 memberId만 담고 role 클레임은 없다")
    void createRefreshToken_containsOnlyMemberId() {
        String token = jwtTokenProvider.createRefreshToken(1L);

        assertThat(jwtTokenProvider.getMemberId(token)).isEqualTo(1L);
        assertThat(jwtTokenProvider.getRole(token)).isNull();
        assertThat(jwtTokenProvider.isValid(token)).isTrue();
    }

    @Test
    @DisplayName("서명이 다른 키로 발급된 토큰은 유효하지 않다")
    void isValid_wrongSignature_returnsFalse() {
        JwtProperties otherProperties = new JwtProperties(
                "another-secret-key-for-jwt-token-provider-unit-test-32bytes+",
                1_800_000L,
                1_209_600_000L);
        JwtTokenProvider otherProvider = new JwtTokenProvider(otherProperties);
        String token = otherProvider.createAccessToken(1L, Role.CUSTOMER);

        assertThat(jwtTokenProvider.isValid(token)).isFalse();
        assertThatThrownBy(() -> jwtTokenProvider.getMemberId(token))
                .isInstanceOf(SignatureException.class);
    }
}
