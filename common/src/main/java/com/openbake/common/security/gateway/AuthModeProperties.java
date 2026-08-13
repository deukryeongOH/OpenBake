package com.openbake.common.security.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openbake.security")
public record AuthModeProperties(
        AuthMode authMode
) {
    public AuthModeProperties {
        if (authMode == null) {
            authMode = AuthMode.DUAL;
        }
    }
}