package com.openbake.member.infrastructure.oauth;

import com.openbake.member.domain.AuthProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(OAuthProviderProperties.class)
public class OidcJwtDecoderConfig {

    @Bean
    public Map<AuthProvider, JwtDecoder> providerJwtDecoders(OAuthProviderProperties properties) {
        Map<AuthProvider, JwtDecoder> decoders = new EnumMap<>(AuthProvider.class);

        properties.providers().forEach((key, provider) -> {
            AuthProvider authProvider = AuthProvider.valueOf(key.toUpperCase());
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(provider.jwkSetUri()).build();

            OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(provider.issuer());
            OAuth2TokenValidator<Jwt> withAudience = new JwtClaimValidator<List<String>>(
                    JwtClaimNames.AUD, aud -> aud != null && aud.contains(provider.clientId()));
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience));

            decoders.put(authProvider, decoder);
        });

        return decoders;
    }
}
