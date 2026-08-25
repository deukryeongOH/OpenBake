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

    private static final Pattern OPTIONAL_PRODUCT_DETAIL_PATH =
            Pattern.compile("^/api/v1/products/[1-9][0-9]*$");

    private static final Pattern OPTIONAL_DROP_DETAIL_PATH =
            Pattern.compile("^/api/v1/drops/[1-9][0-9]*/info$");

    // 홈/카테고리/검색 화면의 목록·자동완성 조회 — 개인화 없이 누구나 볼 수 있는 데이터라
    // 상세 조회와 같은 optional-auth로 연다. 추천(recommendations)은 개인화가 본질이라 제외.
    private static final Pattern OPTIONAL_PRODUCT_LIST_PATH =
            Pattern.compile("^/api/v1/products/product-list$");

    private static final Pattern OPTIONAL_PRODUCT_AUTOCOMPLETE_PATH =
            Pattern.compile("^/api/v1/products/autocomplete$");

    private static final Pattern OPTIONAL_DROP_UPCOMING_PATH =
            Pattern.compile("^/api/v1/drops/upcoming$");

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

    public boolean isOptionallyAuthenticated(ServerHttpRequest request) {
        HttpMethod method = request.getMethod();
        String rawPath = request.getURI().getRawPath();
        if (method != HttpMethod.GET || rawPath == null) {
            return false;
        }
        return OPTIONAL_PRODUCT_DETAIL_PATH.matcher(rawPath).matches()
                || OPTIONAL_DROP_DETAIL_PATH.matcher(rawPath).matches()
                || OPTIONAL_PRODUCT_LIST_PATH.matcher(rawPath).matches()
                || OPTIONAL_PRODUCT_AUTOCOMPLETE_PATH.matcher(rawPath).matches()
                || OPTIONAL_DROP_UPCOMING_PATH.matcher(rawPath).matches();
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
