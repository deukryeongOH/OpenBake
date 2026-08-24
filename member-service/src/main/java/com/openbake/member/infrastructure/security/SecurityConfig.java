package com.openbake.member.infrastructure.security;

import com.openbake.common.security.Authorities;
import com.openbake.common.security.ServiceAuthenticationFilter;
import com.openbake.common.security.ServiceCredential;
import com.openbake.common.security.gateway.HeaderAuthenticationFilter;
import com.openbake.common.security.service.ServiceAuthenticationHeaders;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain internalMemberFilterChain(
            HttpSecurity http,
            @Value("${CORE_SERVICE_TOKEN}") String coreServiceToken) throws Exception {
        http
                .securityMatcher("/internal/v1/members/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().hasAuthority(Authorities.SERVICE_CORE))
                .addFilterBefore(
                        new ServiceAuthenticationFilter(List.of(
                                ServiceCredential.of(
                                        ServiceAuthenticationHeaders.CORE_SERVICE,
                                        Authorities.SERVICE_CORE,
                                        coreServiceToken,
                                        SecurityConfig::isInternalMemberPath))),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        /** Actuator와 Prometheus */
                        .requestMatchers( "/actuator/health",
                                "/actuator/prometheus",
                                "/actuator/metrics",
                                "/actuator/metrics/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new HeaderAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private static boolean isInternalMemberPath(String path) {
        return path != null && path.startsWith("/internal/v1/members/");
    }

}
