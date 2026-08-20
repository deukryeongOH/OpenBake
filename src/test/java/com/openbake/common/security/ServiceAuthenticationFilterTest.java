package com.openbake.common.security;

import static com.openbake.common.security.service.ServiceAuthenticationHeaders.AI_SERVICE;
import static com.openbake.common.security.service.ServiceAuthenticationHeaders.SERVICE_NAME;
import static com.openbake.common.security.service.ServiceAuthenticationHeaders.SERVICE_TOKEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.FilterChain;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class ServiceAuthenticationFilterTest {

    private final String token = UUID.randomUUID().toString();
    private final ServiceAuthenticationFilter filter = new ServiceAuthenticationFilter(token);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsMissingOrMismatchedTokenWithUnauthorized() throws Exception {
        MockHttpServletResponse missing = invoke(null, AI_SERVICE);
        MockHttpServletResponse mismatched = invoke(UUID.randomUUID().toString(), AI_SERVICE);

        assertEquals(401, missing.getStatus());
        assertEquals(401, mismatched.getStatus());
    }

    @Test
    void checksServiceNameAfterValidTokenAndRejectsDifferentService() throws Exception {
        MockHttpServletResponse response = invoke(token, "core-service");

        assertEquals(403, response.getStatus());
    }

    @Test
    void grantsServiceAiAuthorityForValidHeaders() throws Exception {
        MockHttpServletRequest request = request(token, AI_SERVICE);
        MockHttpServletResponse response = new MockHttpServletResponse();
        final boolean[] invoked = {false};
        FilterChain chain = (ignoredRequest, ignoredResponse) -> invoked[0] = true;

        filter.doFilter(request, response, chain);

        assertTrue(invoked[0]);
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> Authorities.SERVICE_AI.equals(authority.getAuthority())));
    }

    private MockHttpServletResponse invoke(String suppliedToken, String serviceName) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(suppliedToken, serviceName), response,
                (ignoredRequest, ignoredResponse) -> {
                });
        return response;
    }

    private MockHttpServletRequest request(String suppliedToken, String serviceName) {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/internal/v1/products/recommendation-candidates");
        if (suppliedToken != null) {
            request.addHeader(SERVICE_TOKEN, suppliedToken);
        }
        request.addHeader(SERVICE_NAME, serviceName);
        return request;
    }
}
