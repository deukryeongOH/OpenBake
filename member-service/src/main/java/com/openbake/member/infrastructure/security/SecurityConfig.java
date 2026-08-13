package com.openbake.member.infrastructure.security;

import com.openbake.common.security.gateway.DualValidationMismatchFilter;
import com.openbake.common.security.gateway.AuthModeProperties;
import com.openbake.common.security.gateway.HeaderAuthenticationFilter;
import com.openbake.common.security.jwt.AccessTokenRepository;
import com.openbake.common.security.jwt.JwtAuthenticationFilter;
import com.openbake.common.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableConfigurationProperties(AuthModeProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final AccessTokenRepository accessTokenRepository;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AuthModeProperties authModeProperties) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/internal/v1/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .anyRequest().authenticated()
                );

        switch (authModeProperties.authMode()) {
            case JWT -> {
                JwtAuthenticationFilter jwt =
                        new JwtAuthenticationFilter(
                                jwtTokenProvider,
                                accessTokenRepository
                        );

                http.addFilterBefore(
                        jwt,
                        UsernamePasswordAuthenticationFilter.class
                );
            }

            case DUAL -> {
                JwtAuthenticationFilter jwt =
                        new JwtAuthenticationFilter(
                                jwtTokenProvider,
                                accessTokenRepository
                        );

                http
                        .addFilterBefore(
                                jwt,
                                UsernamePasswordAuthenticationFilter.class
                        )
                        .addFilterAfter(
                                new DualValidationMismatchFilter(),
                                JwtAuthenticationFilter.class
                        );
            }

            case HEADER -> http.addFilterBefore(
                    new HeaderAuthenticationFilter(),
                    UsernamePasswordAuthenticationFilter.class
            );
        }

        return http.build();
    }

}
