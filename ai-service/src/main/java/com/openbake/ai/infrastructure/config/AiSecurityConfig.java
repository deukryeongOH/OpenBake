package com.openbake.ai.infrastructure.config;

import com.openbake.common.security.CurrentMemberProvider;
import com.openbake.common.security.gateway.HeaderAuthenticationFilter;
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
    SecurityFilterChain aiSecurityFilterChain(HttpSecurity http) throws Exception {
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
                        .requestMatchers(HttpMethod.GET, "/api/v1/recommendations").authenticated()
                        .anyRequest().authenticated())
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
