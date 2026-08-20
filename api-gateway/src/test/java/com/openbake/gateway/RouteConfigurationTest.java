package com.openbake.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "MEMBER_SERVICE_URL=http://member.test:8081",
        "PAYMENT_SERVICE_URL=http://payment.test:8082",
        "CORE_SERVICE_URL=http://core.test:8080",
        "AI_SERVICE_URL=http://ai.test:8083",
        "jwt.secret=openbake-test-secret-must-be-at-least-32-bytes"
})
class RouteConfigurationTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void loadsExpectedRoutesWithCorrectTargetsAndOrder() {
        Map<String, Route> routes = loadRoutes();

        assertEquals(
                Set.of(
                        "member-auth",
                        "member-api",
                        "payment-webhook",
                        "payment-api",
                        "ai-recommendation",
                        "core-swagger-ui",
                        "core-api-docs",
                        "core-api"
                ),
                routes.keySet()
        );

        assertRoute(
                routes,
                "ai-recommendation",
                "http://ai.test:8083",
                -30
        );
        assertRoute(
                routes,
                "member-auth",
                "http://member.test:8081",
                -100
        );
        assertRoute(
                routes,
                "member-api",
                "http://member.test:8081",
                -90
        );
        assertRoute(
                routes,
                "payment-webhook",
                "http://payment.test:8082",
                -80
        );
        assertRoute(
                routes,
                "payment-api",
                "http://payment.test:8082",
                -70
        );
        assertRoute(
                routes,
                "core-swagger-ui",
                "http://core.test:8080",
                -20
        );
        assertRoute(
                routes,
                "core-api-docs",
                "http://core.test:8080",
                -10
        );
        assertRoute(
                routes,
                "core-api",
                "http://core.test:8080",
                0
        );
    }

    @Test
    void matchesExpectedExternalPaths() {
        Map<String, Route> routes = loadRoutes();

        assertTrue(matches(
                routes.get("member-auth"),
                "/api/v1/auth/login"
        ));
        assertTrue(matches(
                routes.get("member-api"),
                "/api/v1/members/1"
        ));
        assertTrue(matches(
                routes.get("payment-webhook"),
                "/api/v1/webhooks/pg/toss"
        ));
        assertTrue(matches(
                routes.get("payment-api"),
                "/api/v1/deposit/account"
        ));
        assertTrue(matches(
                routes.get("ai-recommendation"),
                "/api/v1/recommendations"
        ));
        assertTrue(matches(
                routes.get("core-swagger-ui"),
                "/swagger-ui.html"
        ));
        assertTrue(matches(
                routes.get("core-swagger-ui"),
                "/swagger-ui/index.html"
        ));
        assertTrue(matches(
                routes.get("core-api-docs"),
                "/v3/api-docs"
        ));
        assertTrue(matches(
                routes.get("core-api-docs"),
                "/v3/api-docs/swagger-config"
        ));
        assertTrue(matches(
                routes.get("core-api"),
                "/api/v1/sellers/1"
        ));
        assertTrue(routes.get("ai-recommendation").getOrder()
                < routes.get("core-api").getOrder());
    }

    @Test
    void doesNotExposeInternalPaths() {
        Map<String, Route> routes = loadRoutes();

        boolean anyRouteMatchesInternalPath = routes.values()
                .stream()
                .anyMatch(route ->
                        matches(route, "/internal/v1/members/1"));

        assertFalse(anyRouteMatchesInternalPath);
    }

    private Map<String, Route> loadRoutes() {
        Map<String, Route> routes = routeLocator.getRoutes()
                .collectMap(Route::getId)
                .block();

        assertNotNull(routes);
        return routes;
    }

    private void assertRoute(
            Map<String, Route> routes,
            String id,
            String expectedUri,
            int expectedOrder
    ) {
        Route route = routes.get(id);

        assertNotNull(route, "route가 없습니다: " + id);
        assertEquals(URI.create(expectedUri), route.getUri());
        assertEquals(expectedOrder, route.getOrder());
    }

    private boolean matches(Route route, String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(path).build()
        );

        Boolean matched = Mono.from(
                route.getPredicate().apply(exchange)
        ).block();

        return Boolean.TRUE.equals(matched);
    }
}
