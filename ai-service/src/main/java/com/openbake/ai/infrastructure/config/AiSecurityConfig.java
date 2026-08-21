package com.openbake.ai.infrastructure.config;

import com.openbake.common.security.CurrentMemberProvider;
import com.openbake.common.security.gateway.HeaderAuthenticationFilter;
import com.openbake.common.security.ServiceAuthenticationFilter;
import com.openbake.common.security.service.AiServicePaths;
import java.time.Clock;
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
public class AiSecurityConfig {

    @Bean
    SecurityFilterChain aiSecurityFilterChain(
            HttpSecurity http,
            @org.springframework.beans.factory.annotation.Value("${AI_SERVICE_TOKEN}")
            String aiServiceToken) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html",
                                "/actuator/health", "/actuator/health/**",
                                "/actuator/prometheus", "/actuator/metrics", "/actuator/metrics/**")
                        .permitAll()
                        .requestMatchers(AiServicePaths.AI_OPERATIONS_PATTERN).hasAuthority("SERVICE_AI")
                        .requestMatchers(HttpMethod.GET, "/api/v1/recommendations").authenticated()
                        .anyRequest().authenticated())
                .addFilterBefore(
                        new ServiceAuthenticationFilter(aiServiceToken),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new HeaderAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CurrentMemberProvider currentMemberProvider() {
        return new CurrentMemberProvider();
    }

    @Bean
    Clock recommendationClock() {
        return Clock.systemUTC();
    }
}
