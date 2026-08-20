package com.openbake.common.security;

import static com.openbake.common.security.service.ServiceAuthenticationHeaders.AI_SERVICE;
import static com.openbake.common.security.service.ServiceAuthenticationHeaders.SERVICE_NAME;
import static com.openbake.common.security.service.ServiceAuthenticationHeaders.SERVICE_TOKEN;

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

public final class ServiceAuthenticationFilter extends OncePerRequestFilter {

    private static final String CANDIDATES_PATH =
            "/internal/v1/products/recommendation-candidates";
    private static final String LATEST_CANDIDATES_PATH =
            "/internal/v1/products/latest-recommendation-candidates";

    private final byte[] expectedToken;

    public ServiceAuthenticationFilter(String expectedToken) {
        if (expectedToken == null || expectedToken.isBlank()) {
            throw new IllegalArgumentException("AI service token must be configured");
        }
        this.expectedToken = expectedToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !CANDIDATES_PATH.equals(path) && !LATEST_CANDIDATES_PATH.equals(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String suppliedToken = request.getHeader(SERVICE_TOKEN);
        if (!constantTimeEquals(suppliedToken)) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        if (!AI_SERVICE.equals(request.getHeader(SERVICE_NAME))) {
            reject(response, HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                AI_SERVICE,
                null,
                List.of(new SimpleGrantedAuthority(Authorities.SERVICE_AI)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private boolean constantTimeEquals(String suppliedToken) {
        byte[] supplied = suppliedToken == null
                ? new byte[0]
                : suppliedToken.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedToken, supplied);
    }

    private void reject(HttpServletResponse response, int status) {
        SecurityContextHolder.clearContext();
        response.setStatus(status);
    }
}
