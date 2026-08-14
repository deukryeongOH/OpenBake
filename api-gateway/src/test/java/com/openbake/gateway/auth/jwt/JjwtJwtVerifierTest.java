package com.openbake.gateway.auth.jwt;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JjwtJwtVerifierTest {

    private static final String SECRET =
            "openbake-test-secret-must-be-at-least-32-bytes";

    private static final String OTHER_SECRET =
            "different-test-secret-must-be-at-least-32-bytes";

    private final JjwtJwtVerifier verifier =
            new JjwtJwtVerifier(SECRET);

    @Test
    void verifiesValidCustomerAccessToken() {
        String token = createToken(
                SECRET,
                "42",
                "CUSTOMER",
                Instant.now().plusSeconds(300)
        );

        JwtClaims claims = verifier.verify(token);

        assertEquals(42L, claims.memberId());
        assertEquals("CUSTOMER", claims.role());
    }

    @Test
    void verifiesValidAdminAccessToken() {
        String token = createToken(
                SECRET,
                "7",
                "ADMIN",
                Instant.now().plusSeconds(300)
        );

        JwtClaims claims = verifier.verify(token);

        assertEquals(7L, claims.memberId());
        assertEquals("ADMIN", claims.role());
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        String token = createToken(
                OTHER_SECRET,
                "42",
                "CUSTOMER",
                Instant.now().plusSeconds(300)
        );

        JwtVerificationException exception = assertThrows(
                JwtVerificationException.class,
                () -> verifier.verify(token)
        );

        assertEquals(
                JwtVerificationError.INVALID_SIGNATURE,
                exception.error()
        );
        assertEquals("TOKEN_INVALID", exception.safeCode());
        assertFalse(exception.getMessage().contains(token));
    }

    @Test
    void rejectsExpiredToken() {
        String token = createToken(
                SECRET,
                "42",
                "CUSTOMER",
                Instant.now().minusSeconds(60)
        );

        JwtVerificationException exception = assertThrows(
                JwtVerificationException.class,
                () -> verifier.verify(token)
        );

        assertEquals(
                JwtVerificationError.EXPIRED,
                exception.error()
        );
        assertEquals("TOKEN_EXPIRED", exception.safeCode());
    }

    @Test
    void rejectsNonNumericSubject() {
        String token = createToken(
                SECRET,
                "not-a-number",
                "CUSTOMER",
                Instant.now().plusSeconds(300)
        );

        assertInvalidClaims(token);
    }

    @Test
    void rejectsNonPositiveMemberId() {
        String token = createToken(
                SECRET,
                "0",
                "CUSTOMER",
                Instant.now().plusSeconds(300)
        );

        assertInvalidClaims(token);
    }

    @Test
    void rejectsMissingRole() {
        String token = createToken(
                SECRET,
                "42",
                null,
                Instant.now().plusSeconds(300)
        );

        assertInvalidClaims(token);
    }

    @Test
    void rejectsUnknownRole() {
        String token = createToken(
                SECRET,
                "42",
                "SELLER",
                Instant.now().plusSeconds(300)
        );

        assertInvalidClaims(token);
    }

    @Test
    void rejectsBlankTokenWithoutLeakingIt() {
        JwtVerificationException exception = assertThrows(
                JwtVerificationException.class,
                () -> verifier.verify("   ")
        );

        assertEquals(
                JwtVerificationError.MALFORMED,
                exception.error()
        );
        assertEquals("TOKEN_INVALID", exception.safeCode());
    }

    @Test
    void rejectsBlankSecretAtConstruction() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new JjwtJwtVerifier(" ")
        );
    }

    private void assertInvalidClaims(String token) {
        JwtVerificationException exception = assertThrows(
                JwtVerificationException.class,
                () -> verifier.verify(token)
        );

        assertEquals(
                JwtVerificationError.INVALID_CLAIMS,
                exception.error()
        );
        assertEquals(
                "TOKEN_CLAIMS_INVALID",
                exception.safeCode()
        );
    }

    private String createToken(
            String secret,
            String subject,
            String role,
            Instant expiration
    ) {
        SecretKey key = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );

        JwtBuilder builder = Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiration));

        if (role != null) {
            builder.claim("role", role);
        }

        return builder.signWith(key).compact();
    }
}