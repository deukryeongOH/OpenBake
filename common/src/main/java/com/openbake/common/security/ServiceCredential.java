package com.openbake.common.security;

import java.nio.charset.StandardCharsets;
import java.util.function.Predicate;

/** 서비스 이름·토큰·부여 권한·적용 경로를 하나로 묶은 인증 단위다. */
public record ServiceCredential(
        String serviceName,
        String authority,
        byte[] token,
        Predicate<String> pathMatcher) {

    public static ServiceCredential of(
            String serviceName, String authority, String token, Predicate<String> pathMatcher) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(
                    "Service token must be configured for " + serviceName);
        }
        return new ServiceCredential(
                serviceName, authority, token.getBytes(StandardCharsets.UTF_8), pathMatcher);
    }
}
