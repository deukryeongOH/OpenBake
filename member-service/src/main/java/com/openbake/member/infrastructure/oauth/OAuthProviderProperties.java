package com.openbake.member.infrastructure.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "oauth")
public record OAuthProviderProperties(Map<String, Provider> providers) {

    public record Provider(String issuer, String jwkSetUri, String clientId) {}
}
