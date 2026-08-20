package com.openbake.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class IdentityHeaderSanitizingFilterTest {

    private final IdentityHeaderSanitizingFilter filter =
            new IdentityHeaderSanitizingFilter();

    @Test
    void removesUntrustedIdentityHeadersButPreservesAuthorization() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/members/1")
                .header("X-Openbake-Member-Id", "999")
                .header("X-Openbake-Member-Role", "ADMIN")
                .header("X-Openbake-Auth-Source", "external")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .build();

        MockServerWebExchange exchange =
                MockServerWebExchange.from(request);

        AtomicReference<ServerWebExchange> forwardedExchange =
                new AtomicReference<>();

        GatewayFilterChain chain = filteredExchange -> {
            forwardedExchange.set(filteredExchange);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        HttpHeaders forwardedHeaders =
                forwardedExchange.get().getRequest().getHeaders();

        assertNull(forwardedHeaders.getFirst("X-Openbake-Member-Id"));
        assertNull(forwardedHeaders.getFirst("X-Openbake-Member-Role"));
        assertNull(forwardedHeaders.getFirst("X-Openbake-Auth-Source"));
        assertEquals(
                "Bearer test-token",
                forwardedHeaders.getFirst(HttpHeaders.AUTHORIZATION)
        );
    }

    @Test
    void runsAtHighestPrecedence() {
        assertEquals(Ordered.HIGHEST_PRECEDENCE, filter.getOrder());
    }
}
