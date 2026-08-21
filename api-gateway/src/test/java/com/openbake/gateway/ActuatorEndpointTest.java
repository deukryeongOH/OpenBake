package com.openbake.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * k3s 매니페스트(이슈 3)가 사용할 probe/metric endpoint가 실제로 응답하는지 확인한다.
 * health 집계에는 Redis 인디케이터가 포함되는데, 로컬에 Redis 가 떠 있지 않으면
 * 상태가 DOWN 이 되어 이 테스트가 실패하므로 GatewaySmokeTest 와 동일하게 제외한다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "jwt.secret=openbake-test-secret-must-be-at-least-32-bytes",
                "management.health.redis.enabled=false"
        }
)
class ActuatorEndpointTest {

    @Value("${local.server.port}")
    private int port;

    @Test
    void prometheusEndpoint_returnsMetricsWithApplicationTag() throws IOException, InterruptedException {
        HttpResponse<String> response = get("/actuator/prometheus");

        assertEquals(200, response.statusCode());
        assertFalse(response.body().isBlank());
        assertTrue(response.body().contains("application=\"api-gateway\""));
    }

    @Test
    void livenessEndpoint_returnsOk() throws IOException, InterruptedException {
        HttpResponse<String> response = get("/actuator/health/liveness");

        assertEquals(200, response.statusCode());
    }

    @Test
    void readinessEndpoint_returnsOk() throws IOException, InterruptedException {
        HttpResponse<String> response = get("/actuator/health/readiness");

        assertEquals(200, response.statusCode());
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET()
                .build();

        return HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
    }
}
