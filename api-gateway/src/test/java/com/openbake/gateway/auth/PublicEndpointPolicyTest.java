package com.openbake.gateway.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import java.net.URI;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicEndpointPolicyTest {

    private final PublicEndpointPolicy policy =
            new PublicEndpointPolicy();

    @ParameterizedTest
    @MethodSource("exactPublicEndpoints")
    void allowsApprovedExactEndpoint(
            HttpMethod method,
            String path
    ) {
        assertTrue(policy.isPublic(request(method, path)));
    }

    static Stream<Arguments> exactPublicEndpoints() {
        return Stream.of(
                Arguments.of(
                        HttpMethod.POST,
                        "/api/v1/auth/signup"
                ),
                Arguments.of(
                        HttpMethod.POST,
                        "/api/v1/auth/login"
                ),
                Arguments.of(
                        HttpMethod.POST,
                        "/api/v1/auth/reissue"
                ),
                Arguments.of(
                        HttpMethod.POST,
                        "/api/v1/auth/logout"
                ),
                Arguments.of(
                        HttpMethod.POST,
                        "/api/v1/webhooks/pg/toss"
                )
        );
    }

    @Test
    void allowsOnlyGoogleOAuthProvider() {
        assertTrue(policy.isPublic(request(
                HttpMethod.POST,
                "/api/v1/auth/oauth/google"
        )));

        assertFalse(policy.isPublic(request(
                HttpMethod.POST,
                "/api/v1/auth/oauth/local"
        )));

        assertFalse(policy.isPublic(request(
                HttpMethod.POST,
                "/api/v1/auth/oauth/evil"
        )));
    }

    @Test
    void allowsOnlyPositiveNumericSellerId() {
        assertTrue(policy.isPublic(request(
                HttpMethod.GET,
                "/api/v1/sellers/1"
        )));

        assertTrue(policy.isPublic(request(
                HttpMethod.GET,
                "/api/v1/sellers/999999"
        )));

        assertFalse(policy.isPublic(request(
                HttpMethod.GET,
                "/api/v1/sellers/me"
        )));

        assertFalse(policy.isPublic(request(
                HttpMethod.GET,
                "/api/v1/sellers/0"
        )));

        assertFalse(policy.isPublic(request(
                HttpMethod.GET,
                "/api/v1/sellers/-1"
        )));
    }

    @Test
    void requiresExactHttpMethod() {
        assertFalse(policy.isPublic(request(
                HttpMethod.GET,
                "/api/v1/auth/login"
        )));

        assertFalse(policy.isPublic(request(
                HttpMethod.DELETE,
                "/api/v1/auth/logout"
        )));

        assertFalse(policy.isPublic(request(
                HttpMethod.POST,
                "/api/v1/sellers/1"
        )));
    }

    @Test
    void doesNotExposeUnknownAuthEndpointByPrefix() {
        assertFalse(policy.isPublic(request(
                HttpMethod.POST,
                "/api/v1/auth/admin-reset"
        )));

        assertFalse(policy.isPublic(request(
                HttpMethod.POST,
                "/api/v1/auth/login/extra"
        )));
    }

    @Test
    void rejectsEncodedOrAmbiguousPaths() {
        assertFalse(policy.isPublic(request(
                HttpMethod.POST,
                "/api/v1/auth/%6Cogin"
        )));

        assertFalse(policy.isPublic(request(
                HttpMethod.POST,
                "/api/v1/auth/oauth/google%2Fadmin"
        )));

        assertFalse(policy.isPublic(request(
                HttpMethod.POST,
                "/api/v1/auth//login"
        )));

        assertFalse(policy.isPublic(request(
                HttpMethod.POST,
                "/api/v1/auth/../auth/login"
        )));
    }

    @Test
    void queryStringDoesNotChangePathPolicy() {
        assertTrue(policy.isPublic(request(
                HttpMethod.POST,
                "/api/v1/auth/login?source=web"
        )));
    }

    @Test
    void keepsProtectedAndInternalEndpointsProtected() {
        assertFalse(policy.isPublic(request(
                HttpMethod.GET,
                "/api/v1/members/1"
        )));

        assertFalse(policy.isPublic(request(
                HttpMethod.GET,
                "/api/v1/deposit/account"
        )));

        assertFalse(policy.isPublic(request(
                HttpMethod.GET,
                "/internal/v1/members/1"
        )));
    }

    @Test
    void recognizesOnlyDetailGetsAsOptionallyAuthenticated() {
        assertTrue(policy.isOptionallyAuthenticated(request(
                HttpMethod.GET, "/api/v1/products/1")));
        assertTrue(policy.isOptionallyAuthenticated(request(
                HttpMethod.GET, "/api/v1/drops/12/info")));

        assertFalse(policy.isOptionallyAuthenticated(request(
                HttpMethod.GET, "/api/v1/products/0")));
        assertFalse(policy.isOptionallyAuthenticated(request(
                HttpMethod.POST, "/api/v1/products/1")));
        assertFalse(policy.isOptionallyAuthenticated(request(
                HttpMethod.GET, "/api/v1/drops/12")));
    }

    private MockServerHttpRequest request(
            HttpMethod method,
            String uri
    ) {
        return MockServerHttpRequest
                .method(method, URI.create(uri))
                .build();
    }
}
