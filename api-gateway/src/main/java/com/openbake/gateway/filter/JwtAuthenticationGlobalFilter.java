package com.openbake.gateway.filter;

import com.openbake.gateway.auth.PublicEndpointPolicy;
import com.openbake.gateway.auth.ReactiveTokenBlacklist;
import com.openbake.gateway.auth.jwt.JwtClaims;
import com.openbake.gateway.auth.jwt.JwtVerificationException;
import com.openbake.gateway.auth.jwt.JwtVerifier;
import com.openbake.gateway.error.GatewayAuthErrorWriter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

public class JwtAuthenticationGlobalFilter
        implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    private static final String MEMBER_ID_HEADER = "X-Openbake-Member-Id";

    private static final String MEMBER_ROLE_HEADER = "X-Openbake-Member-Role";

    private static final String AUTH_SOURCE_HEADER = "X-Openbake-Auth-Source";

    private static final String AUTH_SOURCE = "api-gateway";

    private final PublicEndpointPolicy publicEndpoints;
    private final JwtVerifier jwtVerifier;
    private final ReactiveTokenBlacklist blacklist;
    private final GatewayAuthErrorWriter errors;
    private final Duration blacklistTimeout;

    public JwtAuthenticationGlobalFilter(
            PublicEndpointPolicy publicEndpoints,
            JwtVerifier jwtVerifier,
            ReactiveTokenBlacklist blacklist,
            GatewayAuthErrorWriter errors,
            Duration blacklistTimeout
    ) {
        this.publicEndpoints = publicEndpoints;
        this.jwtVerifier = jwtVerifier;
        this.blacklist = blacklist;
        this.errors = errors;
        this.blacklistTimeout = blacklistTimeout;
    }

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            GatewayFilterChain chain
    ) {
        if (!isApiRequest(exchange)) {
            return chain.filter(exchange);
        }

        // CORS preflight는 인증 개념이 없는 브라우저 자체 요청이라 무조건 통과시킨다.
        // 실제 CORS 허용 여부는 CorsWebFilter(globalcors 설정)가 별도로 판단한다.
        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        if (publicEndpoints.isPublic(exchange.getRequest())) {
            return chain.filter(exchange);
        }

        boolean optionalAuthentication =
                publicEndpoints.isOptionallyAuthenticated(exchange.getRequest());

        if (optionalAuthentication
                && exchange.getRequest().getHeaders().getOrEmpty(HttpHeaders.AUTHORIZATION).isEmpty()) {
            return chain.filter(exchange);
        }

        TokenExtraction tokenExtraction =
                extractBearerToken(
                        exchange.getRequest().getHeaders()
                );

        if (!tokenExtraction.success()) {
            return errors.unauthorized(
                    exchange,
                    tokenExtraction.errorCode()
            );
        }

        String token = tokenExtraction.token();
        JwtClaims claims;

        try {
            claims = jwtVerifier.verify(token);
        } catch (JwtVerificationException exception) {
            return errors.unauthorized(
                    exchange,
                    exception.safeCode()
            );
        }

        Mono<BlacklistResult> blacklistResult =
                blacklist.contains(token)
                        .timeout(blacklistTimeout)
                        .map(BlacklistResult::available)
                        .onErrorReturn(
                                BlacklistResult.unavailable()
                        );

        return blacklistResult.flatMap(result -> {
            if (!result.available()) {
                return errors.authenticationServiceUnavailable(
                        exchange
                );
            }

            if (result.blocked()) {
                return errors.unauthorized(
                        exchange,
                        "TOKEN_REVOKED"
                );
            }

            ServerWebExchange authenticatedExchange =
                    withVerifiedIdentity(exchange, claims);

            return chain.filter(authenticatedExchange);
        });
    }

    private TokenExtraction extractBearerToken(
            HttpHeaders headers
    ) {
        List<String> values = headers.getOrEmpty(
                HttpHeaders.AUTHORIZATION
        );

        if (values.isEmpty()) {
            return TokenExtraction.failure(
                    "AUTHENTICATION_REQUIRED"
            );
        }

        if (values.size() != 1) {
            return TokenExtraction.failure(
                    "TOKEN_INVALID"
            );
        }

        String authorization = values.getFirst();

        if (authorization == null
                || !authorization.startsWith(BEARER_PREFIX)) {
            return TokenExtraction.failure(
                    "TOKEN_INVALID"
            );
        }

        String token = authorization.substring(
                BEARER_PREFIX.length()
        );

        if (token.isBlank()
                || token.chars()
                .anyMatch(Character::isWhitespace)) {
            return TokenExtraction.failure(
                    "TOKEN_INVALID"
            );
        }

        return TokenExtraction.success(token);
    }

    private boolean isApiRequest(ServerWebExchange exchange) {
        String rawPath = exchange.getRequest()
                .getURI()
                .getRawPath();

        return rawPath != null
                && rawPath.startsWith("/api/");
    }

    private ServerWebExchange withVerifiedIdentity(
            ServerWebExchange exchange,
            JwtClaims claims
    ) {
        ServerHttpRequest authenticatedRequest =
                exchange.getRequest()
                        .mutate()
                        .headers(headers -> {
                            headers.set(
                                    MEMBER_ID_HEADER,
                                    Long.toString(
                                            claims.memberId()
                                    )
                            );
                            headers.set(
                                    MEMBER_ROLE_HEADER,
                                    claims.role()
                            );
                            headers.set(
                                    AUTH_SOURCE_HEADER,
                                    AUTH_SOURCE
                            );
                        })
                        .build();

        return exchange.mutate()
                .request(authenticatedRequest)
                .build();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    private record TokenExtraction(
            String token,
            String errorCode
    ) {
        static TokenExtraction success(String token) {
            return new TokenExtraction(token, null);
        }

        static TokenExtraction failure(String errorCode) {
            return new TokenExtraction(null, errorCode);
        }

        boolean success() {
            return token != null;
        }
    }

    private record BlacklistResult(
            boolean available,
            boolean blocked
    ) {
        static BlacklistResult available(boolean blocked) {
            return new BlacklistResult(true, blocked);
        }

        static BlacklistResult unavailable() {
            return new BlacklistResult(false, false);
        }
    }
}
