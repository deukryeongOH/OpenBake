package com.openbake.common.security.service;

import java.util.Set;

/** SERVICE_AI가 호출할 수 있는 내부 API 경로의 단일 원본이다. */
public final class AiServicePaths {

    public static final String RECOMMENDATION_CANDIDATES =
            "/internal/v1/products/recommendation-candidates";
    public static final String LATEST_RECOMMENDATION_CANDIDATES =
            "/internal/v1/products/latest-recommendation-candidates";
    public static final String PRODUCT_INDEX_SOURCES = "/internal/v1/products/ids";
    public static final String AI_OPERATIONS_PATTERN = "/internal/v1/ai/**";
    public static final String AI_OPERATIONS_PREFIX = "/internal/v1/ai/";

    public static final String[] SECURITY_MATCHERS = {
            RECOMMENDATION_CANDIDATES,
            LATEST_RECOMMENDATION_CANDIDATES,
            PRODUCT_INDEX_SOURCES,
            AI_OPERATIONS_PATTERN
    };

    private static final Set<String> EXACT_PATHS = Set.of(
            RECOMMENDATION_CANDIDATES,
            LATEST_RECOMMENDATION_CANDIDATES,
            PRODUCT_INDEX_SOURCES);

    private AiServicePaths() {
    }

    public static boolean matches(String path) {
        return EXACT_PATHS.contains(path) || path.startsWith(AI_OPERATIONS_PREFIX);
    }
}
