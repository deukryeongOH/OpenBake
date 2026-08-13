package com.openbake.gateway.filter;

import com.openbake.gateway.auth.PublicEndpointPolicy;
import com.openbake.gateway.auth.ReactiveTokenBlacklist;
import com.openbake.gateway.auth.jwt.JwtClaims;
import com.openbake.gateway.auth.jwt.JwtVerificationError;
import com.openbake.gateway.auth.jwt.JwtVerificationException;
import com.openbake.gateway.auth.jwt.JwtVerifier;
import com.openbake.gateway.error.GatewayAuthErrorWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JwtAuthenticationGlobalFilterTest {

    private static final String TOKEN = "valid-access-token";
    private static final String REQUEST_ID = "request-test-123";

    private final JwtVerifier jwtVerifier =
            mock(JwtVerifier.class);

    private final ReactiveTokenBlacklist blacklist =
            mock(ReactiveTokenBlacklist.class);

    private final ObjectMapper objectMapper =
            JsonMapper.builder().build();

    private JwtAuthenticationGlobalFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationGlobalFilter(
                new PublicEndpointPolicy(),
                jwtVerifier,
                blacklist,
                new GatewayAuthErrorWriter(objectMapper),
                Duration.ofMillis(100)
        );
    }

    @Test
    void passesPublicEndpointWithoutJwtValidation() {
        MockServerWebExchange exchange = exchange(
                MockServerHttpRequest
                        .post("/api/v1/auth/login")
                        .build()
        );

        AtomicReference<ServerWebExchange> forwarded =
                new AtomicReference<>();

        filter.filter(
                exchange,
                capturingChain(forwarded)
        ).block();

        assertSame(exchange, forwarded.get());
        verifyNoInteractions(jwtVerifier, blacklist);
    }

    @Test
    void rejectsMissingAuthorizationHeader() throws Exception {
        MockServerWebExchange exchange = protectedExchange();

        AtomicReference<ServerWebExchange> forwarded =
                new AtomicReference<>();

        filter.filter(
                exchange,
                capturingChain(forwarded)
        ).block();

        assertNull(forwarded.get());
        assertError(
                exchange,
                HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_REQUIRED"
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Basic abc",
            "bearer token",
            "Bearer "
    })
    void rejectsInvalidAuthorizationScheme(
            String authorization
    ) throws Exception {
        MockServerWebExchange exchange = exchange(
                MockServerHttpRequest
                        .get("/api/v1/members/1")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                authorization
                        )
                        .build()
        );

        filter.filter(exchange, ignoredChain()).block();

        assertError(
                exchange,
                HttpStatus.UNAUTHORIZED,
                "TOKEN_INVALID"
        );
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        MockServerWebExchange exchange =
                protectedExchangeWithToken();

        when(jwtVerifier.verify(TOKEN))
                .thenThrow(new JwtVerificationException(
                        JwtVerificationError.EXPIRED
                ));

        filter.filter(exchange, ignoredChain()).block();

        assertError(
                exchange,
                HttpStatus.UNAUTHORIZED,
                "TOKEN_EXPIRED"
        );
    }

    @Test
    void rejectsBlacklistedToken() throws Exception {
        MockServerWebExchange exchange =
                protectedExchangeWithToken();

        when(jwtVerifier.verify(TOKEN))
                .thenReturn(new JwtClaims(42L, "CUSTOMER"));

        when(blacklist.contains(TOKEN))
                .thenReturn(Mono.just(true));

        filter.filter(exchange, ignoredChain()).block();

        assertError(
                exchange,
                HttpStatus.UNAUTHORIZED,
                "TOKEN_REVOKED"
        );
    }

    @Test
    void failsClosedWhenRedisIsUnavailable()
            throws Exception {
        MockServerWebExchange exchange =
                protectedExchangeWithToken();

        when(jwtVerifier.verify(TOKEN))
                .thenReturn(new JwtClaims(42L, "CUSTOMER"));

        when(blacklist.contains(TOKEN))
                .thenReturn(Mono.error(
                        new IllegalStateException(
                                "Redis unavailable"
                        )
                ));

        filter.filter(exchange, ignoredChain()).block();

        assertError(
                exchange,
                HttpStatus.SERVICE_UNAVAILABLE,
                "AUTHENTICATION_SERVICE_UNAVAILABLE"
        );
    }

    @Test
    void injectsVerifiedIdentityAndPreservesAuthorization() {
        MockServerWebExchange exchange =
                protectedExchangeWithToken();

        when(jwtVerifier.verify(TOKEN))
                .thenReturn(new JwtClaims(42L, "CUSTOMER"));

        when(blacklist.contains(TOKEN))
                .thenReturn(Mono.just(false));

        AtomicReference<ServerWebExchange> forwarded =
                new AtomicReference<>();

        filter.filter(
                exchange,
                capturingChain(forwarded)
        ).block();

        HttpHeaders headers = forwarded.get()
                .getRequest()
                .getHeaders();

        assertEquals(
                "42",
                headers.getFirst("X-Openbake-Member-Id")
        );
        assertEquals(
                "CUSTOMER",
                headers.getFirst("X-Openbake-Member-Role")
        );
        assertEquals(
                "api-gateway",
                headers.getFirst("X-Openbake-Auth-Source")
        );
        assertEquals(
                "Bearer " + TOKEN,
                headers.getFirst(HttpHeaders.AUTHORIZATION)
        );
    }

    @Test
    void runsAfterSanitizingAndRequestIdFilters() {
        IdentityHeaderSanitizingFilter sanitizing =
                new IdentityHeaderSanitizingFilter();

        RequestIdFilter requestId = new RequestIdFilter();

        assertTrue(
                sanitizing.getOrder() < requestId.getOrder()
        );
        assertTrue(
                requestId.getOrder() < filter.getOrder()
        );
    }

    @Test
    void doesNotAuthenticateActuatorEndpoint() {
        MockServerWebExchange exchange = exchange(
                MockServerHttpRequest
                        .get("/actuator/health")
                        .build()
        );

        AtomicReference<ServerWebExchange> forwarded =
                new AtomicReference<>();

        filter.filter(
                exchange,
                capturingChain(forwarded)
        ).block();

        assertSame(exchange, forwarded.get());
        verifyNoInteractions(jwtVerifier, blacklist);
    }

    @Test
    void doesNotTurnUnroutedInternalPathIntoAuthenticationError() {
        MockServerWebExchange exchange = exchange(
                MockServerHttpRequest
                        .get("/internal/v1/members/1")
                        .build()
        );

        AtomicReference<ServerWebExchange> forwarded =
                new AtomicReference<>();

        filter.filter(
                exchange,
                capturingChain(forwarded)
        ).block();

        assertSame(exchange, forwarded.get());
        verifyNoInteractions(jwtVerifier, blacklist);
    }

    private MockServerWebExchange protectedExchange() {
        return exchange(
                MockServerHttpRequest
                        .get("/api/v1/members/1")
                        .build()
        );
    }

    private MockServerWebExchange protectedExchangeWithToken() {
        return exchange(
                MockServerHttpRequest
                        .get("/api/v1/members/1")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TOKEN
                        )
                        .build()
        );
    }

    private MockServerWebExchange exchange(
            MockServerHttpRequest request
    ) {
        MockServerWebExchange exchange =
                MockServerWebExchange.from(request);

        exchange.getResponse()
                .getHeaders()
                .set("X-Request-Id", REQUEST_ID);

        return exchange;
    }

    private GatewayFilterChain capturingChain(
            AtomicReference<ServerWebExchange> forwarded
    ) {
        return filteredExchange -> {
            forwarded.set(filteredExchange);
            return Mono.empty();
        };
    }

    private GatewayFilterChain ignoredChain() {
        return filteredExchange -> Mono.empty();
    }

    private void assertError(
            MockServerWebExchange exchange,
            HttpStatus expectedStatus,
            String expectedCode
    ) throws Exception {
        assertEquals(
                expectedStatus,
                exchange.getResponse().getStatusCode()
        );

        String json = exchange.getResponse()
                .getBodyAsString()
                .block();

        JsonNode body = objectMapper.readTree(json);

        assertEquals(
                false,
                body.get("success").asBoolean()
        );
        assertEquals(
                expectedCode,
                body.get("error").get("code").asString()
        );
        assertEquals(
                REQUEST_ID,
                body.get("requestId").asString()
        );
    }
}