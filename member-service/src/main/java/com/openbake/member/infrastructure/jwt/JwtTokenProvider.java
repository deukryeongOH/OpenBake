package com.openbake.member.infrastructure.jwt;

import com.openbake.member.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
@EnableConfigurationProperties(JwtProperties.class)
public class JwtTokenProvider {

    private static final String ROLE_CLAIM = "role";

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtTokenProvider(JwtProperties properties) {
        this.secretKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = properties.accessTokenExpiration();
        this.refreshTokenExpiration = properties.refreshTokenExpiration();
    }

    public String createAccessToken(Long memberId, Role role) {
        return createToken(memberId, role, accessTokenExpiration);
    }

    public String createRefreshToken(Long memberId) {
        return createToken(memberId, null, refreshTokenExpiration);
    }

    public Long getMemberId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    public Role getRole(String token) {
        String role = parseClaims(token).get(ROLE_CLAIM, String.class);
        return role != null ? Role.valueOf(role) : null;
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private String createToken(Long memberId, Role role, long expiration) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiration);

        JwtBuilder builder = Jwts.builder()
                .subject(String.valueOf(memberId))
                .issuedAt(now)
                .expiration(expiry);

        if (role != null) {
            builder.claim(ROLE_CLAIM, role.name());
        }

        return builder.signWith(secretKey).compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
