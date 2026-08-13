package com.openbake.common.config;

import com.openbake.common.security.gateway.GatewayIdentityHeaders;
import com.openbake.common.security.jwt.AccessTokenRepository;
import com.openbake.common.security.jwt.JwtTokenProvider;
import com.openbake.seller.application.MySellerResult;
import com.openbake.seller.application.SellerService;
import com.openbake.seller.domain.ApplicationStatus;
import com.openbake.seller.presentation.SellerController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = SellerController.class,
        properties = "openbake.security.auth-mode=jwt"
)
@Import(SecurityConfig.class)
@ImportAutoConfiguration(ServletWebSecurityAutoConfiguration.class)
class BackendSecurityJwtModeTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SellerService sellerService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private AccessTokenRepository accessTokenRepository;

    @Test
    void validBearerTokenAuthenticatesProtectedRequest() throws Exception {
        given(jwtTokenProvider.isValid("valid-access-token")).willReturn(true);
        given(accessTokenRepository.isBlacklisted("valid-access-token")).willReturn(false);
        given(jwtTokenProvider.getMemberId("valid-access-token")).willReturn(1L);
        given(jwtTokenProvider.getRole("valid-access-token")).willReturn("CUSTOMER");
        given(sellerService.getMySeller()).willReturn(mySellerResult());

        mockMvc.perform(get("/api/v1/sellers/me")
                        .header("Authorization", "Bearer valid-access-token"))
                .andExpect(status().isOk());
    }

    @Test
    void gatewayHeadersWithoutBearerTokenDoNotAuthenticate() throws Exception {
        mockMvc.perform(get("/api/v1/sellers/me")
                        .header(GatewayIdentityHeaders.MEMBER_ID, "1")
                        .header(GatewayIdentityHeaders.MEMBER_ROLE, "CUSTOMER")
                        .header(
                                GatewayIdentityHeaders.AUTH_SOURCE,
                                GatewayIdentityHeaders.EXPECTED_AUTH_SOURCE
                        ))
                .andExpect(status().isForbidden());
    }

    private MySellerResult mySellerResult() {
        return new MySellerResult(
                1L, 1L, "OpenBake", "123-45-67890",
                ApplicationStatus.APPROVED, null, "088", "110-****-5678",
                true, LocalDateTime.now()
        );
    }
}
