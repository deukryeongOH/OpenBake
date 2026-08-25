package com.openbake.common.config;

import com.openbake.common.security.gateway.HeaderAuthenticationFilter;
import com.openbake.common.security.ServiceAuthenticationFilter;
import com.openbake.common.security.service.AiServicePaths;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
public class SecurityConfig {

    /**
     * .cors(Customizer.withDefaults())는 컨텍스트에서 CorsConfigurationSource 빈을 찾아 그대로 쓴다 —
     * 이 빈이 없으면(원래 상태) 아무 CORS 헤더도 안 붙고, OPTIONS만 permitAll이라 200은 뜨지만
     * Access-Control-Allow-Origin이 없어 브라우저가 preflight 실패로 처리한다. member-service/
     * payment-service와 동일한 원인이라 같은 방식으로 고친다.
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
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            @Value("${openbake.security.ai-service-token}") String aiServiceToken) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/webhooks/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/sellers/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/sellers/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/drops/*/info").permitAll()
                        // 홈/카테고리/검색 게스트 접근 허용 — product-list/autocomplete는 위 "/products/*"
                        // 와일드카드가 이미 커버하지만, drops/upcoming은 "/drops/*/info"(2세그먼트) 패턴에
                        // 걸리지 않아 별도로 명시해야 한다.
                        .requestMatchers(HttpMethod.GET, "/api/v1/drops/upcoming").permitAll()
                        .requestMatchers(AiServicePaths.SECURITY_MATCHERS)
                        .hasAuthority("SERVICE_AI")
                        /** 2. 관리자 전용 내부 API: 현재는 정산만 사용중 */
                        .requestMatchers("/internal/v1/**").hasRole("ADMIN")
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        /** Actuator와 Prometheus */
                        .requestMatchers( "/actuator/health",
                                "/actuator/prometheus",
                                "/actuator/metrics",
                                "/actuator/metrics/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        new ServiceAuthenticationFilter(aiServiceToken),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new HeaderAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
