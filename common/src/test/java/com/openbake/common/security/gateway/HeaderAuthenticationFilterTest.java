package com.openbake.common.security.gateway;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class HeaderAuthenticationFilterTest {

    private final HeaderAuthenticationFilter filter =
            new HeaderAuthenticationFilter();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesValidGatewayIdentityHeaders() throws Exception {
        MockHttpServletRequest request = validRequest();

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isEqualTo(1L);
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_CUSTOMER");
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void rejectsIncompleteGatewayIdentityHeaders() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/members/1");

        request.addHeader(GatewayIdentityHeaders.MEMBER_ID, "1");
        request.addHeader(
                GatewayIdentityHeaders.AUTH_SOURCE,
                GatewayIdentityHeaders.EXPECTED_AUTH_SOURCE
        );

        assertRejected(request);
    }

    @Test
    void rejectsUnknownRole() throws Exception {
        MockHttpServletRequest request = validRequest();

        request.removeHeader(GatewayIdentityHeaders.MEMBER_ROLE);
        request.addHeader(GatewayIdentityHeaders.MEMBER_ROLE, "SELLER");

        assertRejected(request);
    }

    @Test
    void rejectsNonPositiveMemberId() throws Exception {
        MockHttpServletRequest request = validRequest();

        request.removeHeader(GatewayIdentityHeaders.MEMBER_ID);
        request.addHeader(GatewayIdentityHeaders.MEMBER_ID, "0");

        assertRejected(request);
    }

    @Test
    void rejectsInvalidAuthSource() throws Exception {
        MockHttpServletRequest request = validRequest();

        request.removeHeader(GatewayIdentityHeaders.AUTH_SOURCE);
        request.addHeader(GatewayIdentityHeaders.AUTH_SOURCE, "external-client");

        assertRejected(request);
    }

    @Test
    void rejectsDuplicateIdentityHeader() throws Exception {
        MockHttpServletRequest request = validRequest();

        request.addHeader(GatewayIdentityHeaders.MEMBER_ID, "999");

        assertRejected(request);
    }

    @Test
    void rejectsNonNumericMemberId() throws Exception {
        MockHttpServletRequest request = validRequest();

        request.removeHeader(GatewayIdentityHeaders.MEMBER_ID);
        request.addHeader(GatewayIdentityHeaders.MEMBER_ID, "not-a-number");

        assertRejected(request);
    }

    @Test
    void rejectsNegativeMemberId() throws Exception {
        MockHttpServletRequest request = validRequest();

        request.removeHeader(GatewayIdentityHeaders.MEMBER_ID);
        request.addHeader(GatewayIdentityHeaders.MEMBER_ID, "-1");

        assertRejected(request);
    }

    @Test
    void rejectsBlankIdentityHeader() throws Exception {
        MockHttpServletRequest request = validRequest();

        request.removeHeader(GatewayIdentityHeaders.MEMBER_ROLE);
        request.addHeader(GatewayIdentityHeaders.MEMBER_ROLE, " ");

        assertRejected(request);
    }

    @Test
    void rejectsDuplicateRoleHeader() throws Exception {
        MockHttpServletRequest request = validRequest();

        request.addHeader(GatewayIdentityHeaders.MEMBER_ROLE, "ADMIN");

        assertRejected(request);
    }

    @Test
    void rejectsDuplicateAuthSourceHeader() throws Exception {
        MockHttpServletRequest request = validRequest();

        request.addHeader(
                GatewayIdentityHeaders.AUTH_SOURCE,
                GatewayIdentityHeaders.EXPECTED_AUTH_SOURCE
        );

        assertRejected(request);
    }

    @Test
    void skipsPublicAuthEndpoint() throws Exception {
        assertSkipped(new MockHttpServletRequest(
                "POST",
                "/api/v1/auth/login"
        ));
    }

    @Test
    void skipsWebhookEndpoint() throws Exception {
        assertSkipped(new MockHttpServletRequest(
                "POST",
                "/api/v1/webhooks/pg/toss"
        ));
    }

    @Test
    void doesNotSkipInternalEndpoint() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/internal/v1/members/1"
        );

        assertRejected(request);
    }

    @Test
    void authenticatesInternalEndpointWithAdminRole() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/internal/v1/settlement-events/purchase-confirmed"
        );

        request.addHeader(
                GatewayIdentityHeaders.MEMBER_ID,
                "4"
        );
        request.addHeader(
                GatewayIdentityHeaders.MEMBER_ROLE,
                "ADMIN"
        );
        request.addHeader(
                GatewayIdentityHeaders.AUTH_SOURCE,
                GatewayIdentityHeaders.EXPECTED_AUTH_SOURCE
        );

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isEqualTo(4L);
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void skipsPublicSellerDetailEndpoint() throws Exception {
        assertSkipped(new MockHttpServletRequest(
                "GET",
                "/api/v1/sellers/1"
        ));
    }

    @Test
    void doesNotSkipCurrentSellerEndpoint() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/v1/sellers/me"
        );

        assertRejected(request);
    }

    @Test
    void skipsOptionsRequest() throws Exception {
        assertSkipped(new MockHttpServletRequest(
                "OPTIONS",
                "/api/v1/members/1"
        ));
    }

    private MockHttpServletRequest validRequest() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/members/1");

        request.addHeader(GatewayIdentityHeaders.MEMBER_ID, "1");
        request.addHeader(GatewayIdentityHeaders.MEMBER_ROLE, "CUSTOMER");
        request.addHeader(
                GatewayIdentityHeaders.AUTH_SOURCE,
                GatewayIdentityHeaders.EXPECTED_AUTH_SOURCE
        );

        return request;
    }

    private void assertRejected(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNull();
    }

    private void assertSkipped(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
    }
}
