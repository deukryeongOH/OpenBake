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
            blacklistTimeout = Duration.ofMillis(300);
        }

        if (blacklistTimeout.isZero()
                || blacklistTimeout.isNegative()) {
            throw new IllegalArgumentException(
                    "blacklist timeout must be positive"
            );
        }
    }
}