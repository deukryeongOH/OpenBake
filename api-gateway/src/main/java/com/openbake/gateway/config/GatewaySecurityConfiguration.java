package com.openbake.gateway.config;

import com.openbake.gateway.auth.PublicEndpointPolicy;
import com.openbake.gateway.auth.ReactiveTokenBlacklist;
import com.openbake.gateway.auth.jwt.JwtVerifier;
import com.openbake.gateway.error.GatewayAuthErrorWriter;
import com.openbake.gateway.filter.JwtAuthenticationGlobalFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(
        GatewaySecurityProperties.class
)
public class GatewaySecurityConfiguration {

    @Bean
    @ConditionalOnProperty(
            name = "openbake.security.gateway-jwt-enabled",
            havingValue = "true"
    )
    public JwtAuthenticationGlobalFilter
    jwtAuthenticationGlobalFilter(
            PublicEndpointPolicy publicEndpoints,
            JwtVerifier jwtVerifier,
            ReactiveTokenBlacklist blacklist,
            GatewayAuthErrorWriter errors,
            GatewaySecurityProperties properties
    ) {
        return new JwtAuthenticationGlobalFilter(
                publicEndpoints,
                jwtVerifier,
                blacklist,
                errors,
                properties.blacklistTimeout()
        );
    }
}