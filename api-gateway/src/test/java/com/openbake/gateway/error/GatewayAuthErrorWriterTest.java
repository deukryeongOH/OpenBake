package com.openbake.gateway.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayAuthErrorWriterTest {

    private static final String REQUEST_ID =
            "gateway-test-request-123";

    private final ObjectMapper objectMapper =
            JsonMapper.builder().build();

    private final GatewayAuthErrorWriter writer =
            new GatewayAuthErrorWriter(objectMapper);

    @Test
    void writesUnauthorizedJsonWithRequestId() throws Exception {
        MockServerWebExchange exchange = exchangeWithRequestId();

        writer.unauthorized(
                exchange,
                "AUTHENTICATION_REQUIRED"
        ).block();

        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exchange.getResponse().getStatusCode()
        );

        assertJsonContentType(exchange);

        JsonNode body = responseBody(exchange);

        assertEquals(false, body.get("success").asBoolean());
        assertEquals(
                "AUTHENTICATION_REQUIRED",
                body.get("error").get("code").asString()
        );
        assertEquals(
                "인증이 필요합니다.",
                body.get("error").get("message").asString()
        );
        assertEquals(
                REQUEST_ID,
                body.get("requestId").asString()
        );
    }

    @Test
    void writesExpiredTokenAsUnauthorized() throws Exception {
        MockServerWebExchange exchange = exchangeWithRequestId();

        writer.unauthorized(
                exchange,
                "TOKEN_EXPIRED"
        ).block();

        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exchange.getResponse().getStatusCode()
        );

        JsonNode body = responseBody(exchange);

        assertEquals(
                "TOKEN_EXPIRED",
                body.get("error").get("code").asString()
        );
        assertEquals(
                "인증 토큰이 만료되었습니다.",
                body.get("error").get("message").asString()
        );
    }

    @Test
    void writesRedisFailureAsServiceUnavailable()
            throws Exception {
        MockServerWebExchange exchange = exchangeWithRequestId();

        writer.authenticationServiceUnavailable(exchange).block();

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                exchange.getResponse().getStatusCode()
        );

        assertJsonContentType(exchange);

        JsonNode body = responseBody(exchange);

        assertEquals(
                "AUTHENTICATION_SERVICE_UNAVAILABLE",
                body.get("error").get("code").asString()
        );
        assertEquals(
                REQUEST_ID,
                body.get("requestId").asString()
        );
    }

    private MockServerWebExchange exchangeWithRequestId() {
        MockServerWebExchange exchange =
                MockServerWebExchange.from(
                        MockServerHttpRequest
                                .get("/api/v1/members/1")
                                .build()
                );

        exchange.getResponse()
                .getHeaders()
                .set("X-Request-Id", REQUEST_ID);

        return exchange;
    }

    private JsonNode responseBody(
            MockServerWebExchange exchange
    ) throws Exception {
        String json = exchange.getResponse()
                .getBodyAsString()
                .block();

        assertNotNull(json);

        return objectMapper.readTree(json);
    }

    private void assertJsonContentType(
            MockServerWebExchange exchange
    ) {
        MediaType contentType = exchange.getResponse()
                .getHeaders()
                .getContentType();

        assertNotNull(contentType);
        assertTrue(contentType.isCompatibleWith(
                MediaType.APPLICATION_JSON
        ));
    }
}