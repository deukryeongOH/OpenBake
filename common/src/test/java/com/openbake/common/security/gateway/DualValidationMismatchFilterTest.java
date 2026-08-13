package com.openbake.common.security.gateway;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class DualValidationMismatchFilterTest {

    private final DualValidationMismatchFilter filter =
            new DualValidationMismatchFilter();

    private FilterChain filterChain;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filterChain = mock(FilterChain.class);
        response = new MockHttpServletResponse();

        setJwtAuthentication(42L, "CUSTOMER");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void continuesWhenGatewayIdentityMatchesJwtAuthentication()
            throws Exception {
        MockHttpServletRequest request =
                requestWithGatewayIdentity(
                        "42",
                        "CUSTOMER",
                        "api-gateway"
                );

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void rejectsDifferentMemberId() throws Exception {
        MockHttpServletRequest request =
                requestWithGatewayIdentity(
                        "999",
                        "CUSTOMER",
                        "api-gateway"
                );

        filter.doFilter(request, response, filterChain);

        assertRejected();
    }

    @Test
    void rejectsDifferentRole() throws Exception {
        MockHttpServletRequest request =
                requestWithGatewayIdentity(
                        "42",
                        "ADMIN",
                        "api-gateway"
                );

        filter.doFilter(request, response, filterChain);

        assertRejected();
    }

    @Test
    void rejectsUnexpectedAuthSource() throws Exception {
        MockHttpServletRequest request =
                requestWithGatewayIdentity(
                        "42",
                        "CUSTOMER",
                        "attacker"
                );

        filter.doFilter(request, response, filterChain);

        assertRejected();
    }

    @Test
    void rejectsPartialGatewayIdentity() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.addHeader(
                GatewayIdentityHeaders.MEMBER_ID,
                "42"
        );
        request.addHeader(
                GatewayIdentityHeaders.AUTH_SOURCE,
                "api-gateway"
        );

        filter.doFilter(request, response, filterChain);

        assertRejected();
    }

    @Test
    void rejectsNonNumericMemberId() throws Exception {
        MockHttpServletRequest request =
                requestWithGatewayIdentity(
                        "not-a-number",
                        "CUSTOMER",
                        "api-gateway"
                );

        filter.doFilter(request, response, filterChain);

        assertRejected();
    }

    @Test
    void rejectsGatewayIdentityWithoutJwtAuthentication()
            throws Exception {
        SecurityContextHolder.clearContext();

        MockHttpServletRequest request =
                requestWithGatewayIdentity(
                        "42",
                        "CUSTOMER",
                        "api-gateway"
                );

        filter.doFilter(request, response, filterChain);

        assertRejected();
    }

    @Test
    void preservesJwtFallbackWhenGatewayHeadersAreAbsent()
            throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void acceptsMatchingAdminRole() throws Exception {
        setJwtAuthentication(7L, "ADMIN");

        MockHttpServletRequest request =
                requestWithGatewayIdentity(
                        "7",
                        "ADMIN",
                        "api-gateway"
                );

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    private MockHttpServletRequest requestWithGatewayIdentity(
            String memberId,
            String role,
            String authSource
    ) {
        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.addHeader(
                GatewayIdentityHeaders.MEMBER_ID,
                memberId
        );
        request.addHeader(
                GatewayIdentityHeaders.MEMBER_ROLE,
                role
        );
        request.addHeader(
                GatewayIdentityHeaders.AUTH_SOURCE,
                authSource
        );

        return request;
    }

    private void setJwtAuthentication(
            long memberId,
            String role
    ) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        memberId,
                        null,
                        List.of(
                                new SimpleGrantedAuthority(
                                        "ROLE_" + role
                                )
                        )
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);
    }

    private void assertRejected() {
        assertEquals(401, response.getStatus());
        verifyNoInteractions(filterChain);
    }
}