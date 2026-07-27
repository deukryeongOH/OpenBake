package com.openbake.member.infrastructure.oauth;

import com.openbake.common.exception.InvalidIdTokenException;
import com.openbake.member.domain.AuthProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class OidcIdTokenVerifier {

    private final Map<AuthProvider, JwtDecoder> providerJwtDecoders;

    public OidcIdentity verify(AuthProvider provider, String idToken) {
        JwtDecoder decoder = providerJwtDecoders.get(provider);
        if (decoder == null) {
            throw new IllegalStateException("지원하지 않는 로그인 provider입니다: " + provider);
        }

        try {
            Jwt jwt = decoder.decode(idToken);
            return new OidcIdentity(
                    jwt.getSubject(),
                    jwt.getClaimAsString("email"),
                    jwt.getClaimAsString("name"));
        } catch (JwtException e) {
            throw new InvalidIdTokenException();
        }
    }
}
