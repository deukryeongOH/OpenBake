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

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "jwt.secret=openbake-test-secret-must-be-at-least-32-bytes"
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