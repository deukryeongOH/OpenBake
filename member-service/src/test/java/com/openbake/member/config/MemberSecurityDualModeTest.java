package com.openbake.member.config;

import com.openbake.common.security.gateway.GatewayIdentityHeaders;
import com.openbake.common.security.jwt.AccessTokenRepository;
import com.openbake.common.security.jwt.JwtTokenProvider;
import com.openbake.member.application.MemberService;
import com.openbake.member.application.dto.member.MemberResult;
import com.openbake.member.domain.MemberStatus;
import com.openbake.member.domain.Role;
import com.openbake.member.infrastructure.security.SecurityConfig;
import com.openbake.member.presentation.MemberController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = MemberController.class,
        properties = "openbake.security.auth-mode=dual"
)
@Import(SecurityConfig.class)
@ImportAutoConfiguration(ServletWebSecurityAutoConfiguration.class)
class MemberSecurityDualModeTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberService memberService;

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
        given(memberService.getMemberById(1L)).willReturn(memberResult());
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

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validTokenRequest() {
        return get("/api/v1/members/1")
                .header("Authorization", "Bearer valid-access-token");
    }

    private MemberResult memberResult() {
        return new MemberResult(
                1L,
                "홍길동",
                "test@example.com",
                "010-1234-5678",
                Role.CUSTOMER,
                MemberStatus.ACTIVE
        );
    }
}
