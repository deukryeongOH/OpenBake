package com.openbake.payment.infrastructure.security;

import com.openbake.common.security.CurrentMemberProvider;
import com.openbake.common.security.gateway.GatewayIdentityHeaders;
import com.openbake.common.security.jwt.AccessTokenRepository;
import com.openbake.common.security.jwt.JwtTokenProvider;
import com.openbake.payment.application.DepositService;
import com.openbake.payment.application.dto.DepositResult;
import com.openbake.payment.presentation.DepositController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = DepositController.class,
        properties = "openbake.security.auth-mode=dual"
)
@Import({PaymentSecurityConfig.class, CurrentMemberProvider.class})
@ImportAutoConfiguration(ServletWebSecurityAutoConfiguration.class)
class PaymentSecurityDualModeTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DepositService depositService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private AccessTokenRepository accessTokenRepository;

    @BeforeEach
    void setUpValidToken() {
        given(jwtTokenProvider.isValid("valid-access-token")).willReturn(true);
        given(accessTokenRepository.isBlacklisted("valid-access-token")).willReturn(false);
        given(jwtTokenProvider.getMemberId("valid-access-token")).willReturn(1L);
        given(jwtTokenProvider.getRole("valid-access-token")).willReturn("CUSTOMER");
        given(depositService.getBalance(1L)).willReturn(depositResult());
    }

    @Test
    void matchingJwtAndGatewayHeadersAuthenticate() throws Exception {
        mockMvc.perform(validTokenRequest()
                        .header(GatewayIdentityHeaders.MEMBER_ID, "1")
                        .header(GatewayIdentityHeaders.MEMBER_ROLE, "CUSTOMER")
                        .header(
                                GatewayIdentityHeaders.AUTH_SOURCE,
                                GatewayIdentityHeaders.EXPECTED_AUTH_SOURCE
                        ))
                .andExpect(status().isOk());
    }

    @Test
    void jwtWithoutGatewayHeadersUsesRollbackFallback() throws Exception {
        mockMvc.perform(validTokenRequest())
                .andExpect(status().isOk());
    }

    @Test
    void mismatchedMemberIdIsRejected() throws Exception {
        mockMvc.perform(validTokenRequest()
                        .header(GatewayIdentityHeaders.MEMBER_ID, "2")
                        .header(GatewayIdentityHeaders.MEMBER_ROLE, "CUSTOMER")
                        .header(
                                GatewayIdentityHeaders.AUTH_SOURCE,
                                GatewayIdentityHeaders.EXPECTED_AUTH_SOURCE
                        ))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mismatchedRoleIsRejected() throws Exception {
        mockMvc.perform(validTokenRequest()
                        .header(GatewayIdentityHeaders.MEMBER_ID, "1")
                        .header(GatewayIdentityHeaders.MEMBER_ROLE, "ADMIN")
                        .header(
                                GatewayIdentityHeaders.AUTH_SOURCE,
                                GatewayIdentityHeaders.EXPECTED_AUTH_SOURCE
                        ))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder validTokenRequest() {
        return get("/api/v1/deposit/account")
                .header("Authorization", "Bearer valid-access-token");
    }

    private DepositResult depositResult() {
        return new DepositResult(1L, BigDecimal.ZERO, false);
    }
}
