package com.openbake.gateway.config;

import com.openbake.gateway.auth.jwt.JjwtJwtVerifier;
import com.openbake.gateway.auth.jwt.JwtVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfiguration {

    @Bean
    public JwtVerifier jwtVerifier(
            @Value("${jwt.secret}") String secret
    ) {
        return new JjwtJwtVerifier(secret);
    }
}