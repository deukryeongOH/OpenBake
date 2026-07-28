package com.openbake.member.infrastructure.oauth;

import com.openbake.common.exception.InvalidIdTokenException;
import com.openbake.member.domain.AuthProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OidcIdTokenVerifierTest {

    @Mock
    private JwtDecoder googleJwtDecoder;

    private OidcIdTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new OidcIdTokenVerifier(Map.of(AuthProvider.GOOGLE, googleJwtDecoder));
    }

    @Test
    @DisplayName("ID 토큰 검증에 성공하면 sub/email/name을 추출한다")
    void verify_success() {
        Jwt jwt = Jwt.withTokenValue("token-value")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("sub", "google-user-1")
                .claim("email", "test@example.com")
                .claim("name", "홍길동")
                .build();
        given(googleJwtDecoder.decode("id-token")).willReturn(jwt);

        OidcIdentity identity = verifier.verify(AuthProvider.GOOGLE, "id-token");

        assertThat(identity.providerId()).isEqualTo("google-user-1");
        assertThat(identity.email()).isEqualTo("test@example.com");
        assertThat(identity.name()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("토큰 검증에 실패하면 InvalidIdTokenException을 던진다")
    void verify_invalidToken_throwsException() {
        given(googleJwtDecoder.decode("bad-token")).willThrow(new BadJwtException("invalid signature"));

        assertThatThrownBy(() -> verifier.verify(AuthProvider.GOOGLE, "bad-token"))
                .isInstanceOf(InvalidIdTokenException.class);
    }

    @Test
    @DisplayName("지원하지 않는 provider면 IllegalStateException을 던진다")
    void verify_unsupportedProvider_throwsException() {
        assertThatThrownBy(() -> verifier.verify(AuthProvider.LOCAL, "id-token"))
                .isInstanceOf(IllegalStateException.class);
    }
}
