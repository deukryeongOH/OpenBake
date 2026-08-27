package com.openbake.common.security.gateway;

import com.openbake.common.security.service.AiServicePaths;
import com.openbake.common.security.service.CoreServicePaths;
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

    private static final Pattern OPTIONAL_PRODUCT_DETAIL_PATH =
            Pattern.compile("^/api/v1/products/[1-9][0-9]*$");

    private static final Pattern OPTIONAL_DROP_DETAIL_PATH =
            Pattern.compile("^/api/v1/drops/[1-9][0-9]*/info$");

    // 게이트웨이 PublicEndpointPolicy.isOptionallyAuthenticated와 대칭 — 그쪽에서 익명 통과를
    // 허용한 경로는 여기서도 신원 헤더 없이 통과시켜야 한다(안 그러면 헤더가 없다는 이유로
    // 이 필터가 401로 막아버려 게이트웨이 정책이 무의미해진다).
    private static final Pattern OPTIONAL_PRODUCT_LIST_PATH =
            Pattern.compile("^/api/v1/products/product-list$");

    private static final Pattern OPTIONAL_PRODUCT_AUTOCOMPLETE_PATH =
            Pattern.compile("^/api/v1/products/autocomplete$");

    private static final Pattern OPTIONAL_DROP_UPCOMING_PATH =
            Pattern.compile("^/api/v1/drops/upcoming$");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        if ("OPTIONS".equals(method)) {
            return true;
        }

        // actuator, Swagger 등 외부 API가 아닌 요청, /api/** 와 /internal/** 는 인증 필터 적용
        boolean protectedPath =
                path.startsWith("/api/")
                        || path.startsWith("/internal/");

        if (!protectedPath) {
            return true;
        }

        // 서비스 간 인증 경로는 ServiceAuthenticationFilter가 담당한다.
        // 게이트웨이 신원 헤더가 없는 호출이므로 여기서 다시 검사하면 401이 된다.
        if (AiServicePaths.matches(path) || CoreServicePaths.matches(path)) {
            return true;
        }

        if (path.startsWith("/api/v1/auth/")) {
            return true;
        }

        if (path.startsWith("/api/v1/webhooks/")) {
            return true;
        }

        if (!"GET".equals(method)) {
            return false;
        }

        if (PUBLIC_SELLER_PATH.matcher(path).matches()) {
            return true;
        }

        if (OPTIONAL_PRODUCT_DETAIL_PATH.matcher(path).matches()
                || OPTIONAL_DROP_DETAIL_PATH.matcher(path).matches()
                || OPTIONAL_PRODUCT_LIST_PATH.matcher(path).matches()
                || OPTIONAL_PRODUCT_AUTOCOMPLETE_PATH.matcher(path).matches()
                || OPTIONAL_DROP_UPCOMING_PATH.matcher(path).matches()) {
            return request.getHeader(GatewayIdentityHeaders.MEMBER_ID) == null
                    && request.getHeader(GatewayIdentityHeaders.MEMBER_ROLE) == null
                    && request.getHeader(GatewayIdentityHeaders.AUTH_SOURCE) == null;
        }

        return false;
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
