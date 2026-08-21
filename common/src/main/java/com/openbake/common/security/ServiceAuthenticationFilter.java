package com.openbake.common.security;

import static com.openbake.common.security.service.ServiceAuthenticationHeaders.AI_SERVICE;
import static com.openbake.common.security.service.ServiceAuthenticationHeaders.SERVICE_NAME;
import static com.openbake.common.security.service.ServiceAuthenticationHeaders.SERVICE_TOKEN;

import com.openbake.common.security.service.AiServicePaths;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 경로별로 (서비스 이름, 토큰, 권한) 조합이 다를 수 있어 {@link ServiceCredential} 목록으로 일반화했다.
 * 경로 하나에는 credential 하나만 매칭되도록 호출 측에서 pathMatcher를 서로 겹치지 않게 구성해야 한다.
 */
public final class ServiceAuthenticationFilter extends OncePerRequestFilter {

    private final List<ServiceCredential> credentials;

    public ServiceAuthenticationFilter(String expectedToken) {
        this(List.of(ServiceCredential.of(
                AI_SERVICE, Authorities.SERVICE_AI, expectedToken, AiServicePaths::matches)));
    }

    public ServiceAuthenticationFilter(List<ServiceCredential> credentials) {
        if (credentials == null || credentials.isEmpty()) {
            throw new IllegalArgumentException("At least one service credential must be configured");
        }
        this.credentials = List.copyOf(credentials);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return credentials.stream().noneMatch(credential -> credential.pathMatcher().test(path));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        ServiceCredential credential = credentials.stream()
                .filter(candidate -> candidate.pathMatcher().test(path))
                .findFirst()
                .orElse(null);
        if (credential == null) {
            reject(response, HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String suppliedToken = request.getHeader(SERVICE_TOKEN);
        if (!constantTimeEquals(credential.token(), suppliedToken)) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        if (!credential.serviceName().equals(request.getHeader(SERVICE_NAME))) {
            reject(response, HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        var authentication = new UsernamePasswordAuthenticationToken(
                credential.serviceName(), null,
                List.of(new SimpleGrantedAuthority(credential.authority())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private boolean constantTimeEquals(byte[] expectedToken, String suppliedToken) {
        byte[] supplied = suppliedToken == null
                ? new byte[0] : suppliedToken.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedToken, supplied);
    }

    private void reject(HttpServletResponse response, int status) {
        SecurityContextHolder.clearContext();
        response.setStatus(status);
    }
}
