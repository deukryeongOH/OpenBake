package com.openbake.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestIdFilterTest {

    private static final String HEADER = "X-Request-Id";

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void preservesSafeRequestIdInRequestAndResponse() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/members/1")
                        .header(HEADER, "client-request_123.abc")
                        .build()
        );

        AtomicReference<ServerWebExchange> forwardedExchange =
                new AtomicReference<>();

        GatewayFilterChain chain = filteredExchange -> {
            forwardedExchange.set(filteredExchange);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        String forwardedRequestId = forwardedExchange.get()
                .getRequest()
                .getHeaders()
                .getFirst(HEADER);

        String responseRequestId = exchange.getResponse()
                .getHeaders()
                .getFirst(HEADER);

        assertEquals("client-request_123.abc", forwardedRequestId);
        assertEquals(forwardedRequestId, responseRequestId);
    }

    @Test
    void replacesUnsafeRequestIdWithUuid() {
        String unsafeRequestId = "request id with spaces";

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/members/1")
                        .header(HEADER, unsafeRequestId)
                        .build()
        );

        AtomicReference<ServerWebExchange> forwardedExchange =
                new AtomicReference<>();

        GatewayFilterChain chain = filteredExchange -> {
            forwardedExchange.set(filteredExchange);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        String generatedRequestId = forwardedExchange.get()
                .getRequest()
                .getHeaders()
                .getFirst(HEADER);

        String responseRequestId = exchange.getResponse()
                .getHeaders()
                .getFirst(HEADER);

        assertNotEquals(unsafeRequestId, generatedRequestId);
        assertDoesNotThrow(() -> UUID.fromString(generatedRequestId));
        assertEquals(generatedRequestId, responseRequestId);
    }

    @Test
    void runsAfterIdentityHeaderSanitizingFilter() {
        IdentityHeaderSanitizingFilter sanitizingFilter =
                new IdentityHeaderSanitizingFilter();

        assertTrue(sanitizingFilter.getOrder() < filter.getOrder());
    }
}