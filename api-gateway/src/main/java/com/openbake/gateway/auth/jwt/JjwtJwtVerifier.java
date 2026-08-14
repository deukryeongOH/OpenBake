package com.openbake.gateway.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class JjwtJwtVerifier implements JwtVerifier {

    private static final String ROLE_CLAIM = "role";

    private static final Set<String> ALLOWED_ROLES = Set.of("CUSTOMER", "ADMIN");

    private final SecretKey secretKey;

    public JjwtJwtVerifier(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret must not be blank");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public JwtClaims verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw failure(JwtVerificationError.MALFORMED);
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(rawToken)
                    .getPayload();

            long memberId = parseMemberId(claims.getSubject());
            String role = parseRole(claims.get(ROLE_CLAIM, String.class));

            return new JwtClaims(memberId, role);
        } catch (ExpiredJwtException exception) {
            throw failure(JwtVerificationError.EXPIRED);
        } catch (SecurityException exception) {
            throw failure(JwtVerificationError.INVALID_SIGNATURE);
        } catch (MalformedJwtException exception) {
            throw failure(JwtVerificationError.MALFORMED);
        } catch (UnsupportedJwtException exception) {
            throw failure(JwtVerificationError.UNSUPPORTED);
        } catch (JwtVerificationException exception) {
            throw exception;
        } catch (JwtException | IllegalArgumentException exception) {
            throw failure(JwtVerificationError.MALFORMED);
        }
    }

    private long parseMemberId(String subject) {
        if (subject == null || subject.isBlank()) {
            throw failure(JwtVerificationError.INVALID_CLAIMS);
        }

        try {
            long memberId = Long.parseLong(subject);

            if (memberId <= 0) {
                throw failure(JwtVerificationError.INVALID_CLAIMS);
            }

            return memberId;
        } catch (NumberFormatException exception) {
            throw failure(JwtVerificationError.INVALID_CLAIMS);
        }
    }

//    private String parseRole(String role) {
//        if (!ALLOWED_ROLES.contains(role)) {
//            throw failure(JwtVerificationError.INVALID_CLAIMS);
//        }
//
//        return role;
//    }

    private String parseRole(String role) {
        if (role == null
                || role.isBlank()
                || !ALLOWED_ROLES.contains(role)) {
            throw failure(
                    JwtVerificationError.INVALID_CLAIMS
            );
        }

        return role;
    }

    private JwtVerificationException failure(JwtVerificationError error) {
        return new JwtVerificationException(error);
    }
}
