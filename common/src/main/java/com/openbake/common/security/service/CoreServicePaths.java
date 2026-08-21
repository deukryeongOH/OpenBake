package com.openbake.common.security.service;

/** SERVICE_CORE가 호출할 수 있는 내부 API 경로의 단일 원본이다. */
public final class CoreServicePaths {

    public static final String SEMANTIC_SEARCH = "/internal/v1/search/semantic";
    public static final String SEARCH_PATTERN = "/internal/v1/search/**";
    public static final String SEARCH_PREFIX = "/internal/v1/search/";

    public static final String[] SECURITY_MATCHERS = {
            SEARCH_PATTERN
    };

    private CoreServicePaths() {
    }

    public static boolean matches(String path) {
        return path != null && path.startsWith(SEARCH_PREFIX);
    }
}
