package com.openbake.common.security.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class HeaderAuthenticationFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_ROLES =
            Set.of("CUSTOMER", "ADMIN");

    private static final Pattern PUBLIC_SELLER_PATH =
            Pattern.compile("^/api/v1/sellers/[1-9][0-9]*$");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        if ("OPTIONS".equals(method)) {
            return true;
        }

        // internal, actuator, Swagger 등 외부 API가 아닌 요청
        if (!path.startsWith("/api/")) {
            return true;
        }

        if (path.startsWith("/api/v1/auth/")) {
            return true;
        }

        if (path.startsWith("/api/v1/webhooks/")) {
            return true;
        }

        return "GET".equals(method)
                && PUBLIC_SELLER_PATH.matcher(path).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String rawMemberId = singleHeader(
                    request,
                    GatewayIdentityHeaders.MEMBER_ID
            );
            String memberRole = singleHeader(
                    request,
                    GatewayIdentityHeaders.MEMBER_ROLE
            );
            String authSource = singleHeader(
                    request,
                    GatewayIdentityHeaders.AUTH_SOURCE
            );

            long memberId = Long.parseLong(rawMemberId);

            if (memberId <= 0
                    || !GatewayIdentityHeaders.EXPECTED_AUTH_SOURCE.equals(authSource)
                    || !ALLOWED_ROLES.contains(memberRole)) {
                reject(response);
                return;
            }

            var authority = new SimpleGrantedAuthority("ROLE_" + memberRole);
            var authentication = new UsernamePasswordAuthenticationToken(
                    memberId,
                    null,
                    List.of(authority)
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (RuntimeException exception) {
            reject(response);
        }
    }

    private String singleHeader(HttpServletRequest request, String headerName) {
        List<String> values = Collections.list(request.getHeaders(headerName));

        if (values.size() != 1) {
            throw new IllegalArgumentException(
                    "Identity header must have exactly one value"
            );
        }

        String value = values.getFirst();

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    "Identity header must not be blank"
            );
        }

        return value;
    }

    private void reject(HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
