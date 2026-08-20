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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 리액티브 서버가 뜨고 health 엔드포인트가 노출되는지만 확인하는 스모크 테스트다.
 *
 * health 집계에는 Redis 인디케이터가 포함되는데, 로컬에 Redis 가 떠 있지 않으면
 * 상태가 DOWN 이 되어 이 테스트가 실패한다. 검증 대상은 서버 기동 여부이지 Redis 가용성이 아니므로
 * 해당 인디케이터를 제외해 외부 의존성을 끊는다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "jwt.secret=openbake-test-secret-must-be-at-least-32-bytes",
                "management.health.redis.enabled=false"
        }
)
class GatewaySmokeTest {

    @Value("${local.server.port}")
    private int port;

    @Test
    void startsReactiveServerAndExposesHealthEndpoint()
            throws IOException, InterruptedException {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://localhost:" + port + "/actuator/health"
                ))
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"UP\""));
    }
}