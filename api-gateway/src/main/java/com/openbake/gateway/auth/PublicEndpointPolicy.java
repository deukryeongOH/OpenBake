package com.openbake.gateway.auth;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PublicEndpointPolicy {

    private static final Set<Endpoint> EXACT_PUBLIC_ENDPOINTS =
            Set.of(
                    new Endpoint(
                            HttpMethod.POST,
                            "/api/v1/auth/signup"
                    ),
                    new Endpoint(
                            HttpMethod.POST,
                            "/api/v1/auth/login"
                    ),
                    new Endpoint(
                            HttpMethod.POST,
                            "/api/v1/auth/reissue"
                    ),
                    new Endpoint(
                            HttpMethod.POST,
                            "/api/v1/auth/logout"
                    ),
                    new Endpoint(
                            HttpMethod.POST,
                            "/api/v1/webhooks/pg/toss"
                    )
            );

    private static final Pattern OAUTH_PATH = Pattern.compile(
            "^/api/v1/auth/oauth/([^/]+)$"
    );

    private static final Set<String> ALLOWED_OAUTH_PROVIDERS =
            Set.of("google");

    private static final Pattern PUBLIC_SELLER_PATH =
            Pattern.compile("^/api/v1/sellers/[1-9][0-9]*$");

    public boolean isPublic(ServerHttpRequest request) {
        HttpMethod method = request.getMethod();
        String rawPath = request.getURI().getRawPath();

        if (method == null || rawPath == null) {
            return false;
        }

        Endpoint endpoint = new Endpoint(method, rawPath);

        if (EXACT_PUBLIC_ENDPOINTS.contains(endpoint)) {
            return true;
        }

        if (isAllowedOAuthEndpoint(method, rawPath)) {
            return true;
        }

        return method == HttpMethod.GET
                && PUBLIC_SELLER_PATH.matcher(rawPath).matches();
    }

    private boolean isAllowedOAuthEndpoint(
            HttpMethod method,
            String rawPath
    ) {
        if (method != HttpMethod.POST) {
            return false;
        }

        Matcher matcher = OAUTH_PATH.matcher(rawPath);

        return matcher.matches()
                && ALLOWED_OAUTH_PROVIDERS.contains(
                matcher.group(1)
        );
    }

    private record Endpoint(
            HttpMethod method,
            String path
    ) {
    }
}