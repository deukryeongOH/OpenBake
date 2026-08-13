package com.openbake.common.security.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class DualValidationMismatchFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        GatewayIdentity identity = GatewayIdentity.from(request);

        /*
         * Gateway 신원 헤더가 전혀 없으면 기존 JWT 인증 흐름을
         * 유지한다. 이는 dual 단계의 롤백 경로다.
         */
        if (identity.isAbsent()) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (!identity.isComplete() || !identity.hasExpectedSource() || !matches(authentication, identity)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean matches(Authentication authentication, GatewayIdentity identity) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Long jwtMemberId)) {
            return false;
        }

        Long gatewayMemberId = identity.parseMemberId();

        if (gatewayMemberId == null || !gatewayMemberId.equals(jwtMemberId)) {
            return false;
        }

        String expectedAuthority = "ROLE_" + identity.memberRole();

        return authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(expectedAuthority::equals);
    }

    private record GatewayIdentity(
            String memberId,
            String memberRole,
            String authSource
    ) {
        static GatewayIdentity from(HttpServletRequest request) {
            return new GatewayIdentity(
                    request.getHeader(GatewayIdentityHeaders.MEMBER_ID),
                    request.getHeader(GatewayIdentityHeaders.MEMBER_ROLE),
                    request.getHeader(GatewayIdentityHeaders.AUTH_SOURCE)
            );
        }

        boolean isAbsent() {
            return memberId == null && memberRole == null && authSource == null;
        }

        boolean isComplete() {
            return memberId != null && !memberId.isBlank() && memberRole != null
                    && !memberRole.isBlank() && authSource != null && !authSource.isBlank();
        }

        boolean hasExpectedSource() {
            return GatewayIdentityHeaders
                    .EXPECTED_AUTH_SOURCE
                    .equals(authSource);
        }

        Long parseMemberId() {
            try {
                long parsed = Long.parseLong(memberId);
                return parsed > 0 ? parsed : null;
            } catch (NumberFormatException exception) {
                return null;
            }
        }
    }
}