package com.openbake.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "jwt.secret=openbake-test-secret-must-be-at-least-32-bytes",
                "openbake.security.gateway-jwt-enabled=false"
        }
)
class PaymentRouteProxyIntegrationTest {

    private static final AtomicReference<CapturedRequest> CAPTURED_REQUEST =
            new AtomicReference<>();

    private static final HttpServer PAYMENT_SERVER = startPaymentServer();
    private static final HttpServer CORE_SERVER = startCoreServer();

    @Value("${local.server.port}")
    private int gatewayPort;

    @DynamicPropertySource
    static void downstreamProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "PAYMENT_SERVICE_URL",
                () -> "http://localhost:" + PAYMENT_SERVER.getAddress().getPort()
        );
        registry.add(
                "CORE_SERVICE_URL",
                () -> "http://localhost:" + CORE_SERVER.getAddress().getPort()
        );
    }

    @AfterAll
    static void stopServers() {
        PAYMENT_SERVER.stop(0);
        CORE_SERVER.stop(0);
    }

    @Test
    void depositRouteUsesConfiguredPaymentTarget() throws Exception {
        HttpResponse<String> response = send(
                HttpRequest.newBuilder(gatewayUri("/api/v1/deposit/account"))
                        .GET()
                        .build()
        );

        assertEquals(200, response.statusCode());
        assertEquals("payment", response.headers()
                .firstValue("X-Test-Target")
                .orElseThrow());
        assertEquals("payment-response", response.body());
    }

    @Test
    void webhookBodyContentTypeAndSignatureHeaderArePreserved() throws Exception {
        String body = "{\"eventType\":\"PAYMENT_STATUS_CHANGED\",\"data\":{\"paymentKey\":\"pk_test\"}}";

        HttpResponse<String> response = send(
                HttpRequest.newBuilder(gatewayUri("/api/v1/webhooks/pg/toss"))
                        .header("Content-Type", "application/json")
                        .header("X-Test-Pg-Signature", "signature-value")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build()
        );

        CapturedRequest captured = CAPTURED_REQUEST.get();

        assertEquals(200, response.statusCode());
        assertEquals("payment", response.headers()
                .firstValue("X-Test-Target")
                .orElseThrow());
        assertEquals("POST", captured.method());
        assertEquals("/api/v1/webhooks/pg/toss", captured.path());
        assertEquals("application/json", captured.contentType());
        assertEquals("signature-value", captured.signature());
        assertEquals(body, captured.body());
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return HttpClient.newHttpClient().send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private URI gatewayUri(String path) {
        return URI.create("http://localhost:" + gatewayPort + path);
    }

    private static HttpServer startPaymentServer() {
        HttpServer server = createServer();
        server.createContext("/", exchange -> {
            byte[] requestBody = exchange.getRequestBody().readAllBytes();

            CAPTURED_REQUEST.set(new CapturedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getRawPath(),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    exchange.getRequestHeaders().getFirst("X-Test-Pg-Signature"),
                    new String(requestBody, StandardCharsets.UTF_8)
            ));

            respond(exchange, "payment", "payment-response");
        });
        server.start();
        return server;
    }

    private static HttpServer startCoreServer() {
        HttpServer server = createServer();
        server.createContext("/", exchange ->
                respond(exchange, "core", "core-response")
        );
        server.start();
        return server;
    }

    private static HttpServer createServer() {
        try {
            return HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException exception) {
            throw new IllegalStateException("테스트 HTTP 서버를 시작할 수 없습니다.", exception);
        }
    }

    private static void respond(
            HttpExchange exchange,
            String target,
            String body
    ) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("X-Test-Target", target);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private record CapturedRequest(
            String method,
            String path,
            String contentType,
            String signature,
            String body
    ) {
    }
}
