package com.openbake.common.config;

import com.openbake.common.security.gateway.HeaderAuthenticationFilter;
import com.openbake.common.security.ServiceAuthenticationFilter;
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

@Configuration
public class SecurityConfig {

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
                        .requestMatchers(
                                "/internal/v1/products/recommendation-candidates",
                                "/internal/v1/products/latest-recommendation-candidates")
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
