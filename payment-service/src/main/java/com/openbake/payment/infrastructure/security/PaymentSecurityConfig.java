package com.openbake.payment.infrastructure.security;

import com.openbake.common.security.Authorities;
import com.openbake.common.security.ServiceAuthenticationFilter;
import com.openbake.common.security.ServiceCredential;
import com.openbake.common.security.gateway.HeaderAuthenticationFilter;
import com.openbake.common.security.service.ServiceAuthenticationHeaders;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import java.util.Arrays;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class PaymentSecurityConfig {

    /**
     * .cors(Customizer.withDefaults())는 컨텍스트에서 CorsConfigurationSource 빈을 찾아 그대로 쓴다 —
     * 이 빈이 없으면(원래 상태) 아무 CORS 헤더도 안 붙고, OPTIONS만 permitAll이라 200은 뜨지만
     * Access-Control-Allow-Origin이 없어 브라우저가 preflight 실패로 처리한다. member-service와
     * 동일한 원인이라 같은 방식으로 고친다.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${openbake.security.cors.allowed-origins:http://localhost:3000}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        // 명시적 오리진만 허용하므로(와일드카드 아님) 인증 헤더를 실어 보내는 요청도 credentials 모드로 안전하게 허용한다.
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain internalPaymentFilterChain(
            HttpSecurity http,
            @Value("${CORE_SERVICE_TOKEN}") String coreServiceToken) throws Exception {
        http
                .securityMatcher("/internal/v1/payments/**", "/internal/v1/deposits/**")
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
                                        PaymentSecurityConfig::isCoreInternalPath))),
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
                        .requestMatchers("/api/v1/webhooks/**").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
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

    private static boolean isCoreInternalPath(String path) {
        return path != null
                && (path.startsWith("/internal/v1/payments/")
                        || path.startsWith("/internal/v1/deposits/"));
    }
}
