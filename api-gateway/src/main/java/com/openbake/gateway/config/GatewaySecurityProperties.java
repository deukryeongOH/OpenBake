package com.openbake.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "openbake.security")
public record GatewaySecurityProperties(
        boolean gatewayJwtEnabled,
        Duration blacklistTimeout
) {
    public GatewaySecurityProperties {
        if (blacklistTimeout == null) {
            // application.yml 의 기본값과 같은 값으로 둔다. 두 곳이 어긋나면
            // 프로퍼티를 통째로 뺀 환경만 다르게 동작해 찾기 어려운 차이가 된다.
            blacklistTimeout = Duration.ofMillis(1000);
        }

        if (blacklistTimeout.isZero()
                || blacklistTimeout.isNegative()) {
            throw new IllegalArgumentException(
                    "blacklist timeout must be positive"
            );
        }
    }
}