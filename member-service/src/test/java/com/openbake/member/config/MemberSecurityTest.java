package com.openbake.member.config;

import com.openbake.common.security.gateway.GatewayIdentityHeaders;
import com.openbake.common.security.service.ServiceAuthenticationHeaders;
import com.openbake.member.application.MemberInternalService;
import com.openbake.member.application.MemberService;
import com.openbake.member.application.dto.internal.MemberInternalResult;
import com.openbake.member.application.dto.member.MemberResult;
import com.openbake.member.domain.MemberStatus;
import com.openbake.member.domain.Role;
import com.openbake.member.infrastructure.security.SecurityConfig;
import com.openbake.member.presentation.MemberController;
import com.openbake.member.presentation.MemberInternalController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {MemberController.class, MemberInternalController.class})
@Import(SecurityConfig.class)
@ImportAutoConfiguration(ServletWebSecurityAutoConfiguration.class)
@TestPropertySource(properties = "CORE_SERVICE_TOKEN=test-core-service-token")
class MemberSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private MemberInternalService memberInternalService;

    @Test
    void validGatewayHeadersAuthenticateProtectedRequest() throws Exception {
        given(memberService.getMemberById(1L)).willReturn(memberResult());

        mockMvc.perform(get("/api/v1/members/1")
                        .header(GatewayIdentityHeaders.MEMBER_ID, "1")
                        .header(GatewayIdentityHeaders.MEMBER_ROLE, "CUSTOMER")
                        .header(
                                GatewayIdentityHeaders.AUTH_SOURCE,
                                GatewayIdentityHeaders.EXPECTED_AUTH_SOURCE
                        ))
                .andExpect(status().isOk());

    }

    @Test
    void bearerTokenWithoutGatewayHeadersIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/members/1")
                        .header("Authorization", "Bearer valid-access-token"))
                .andExpect(status().isUnauthorized());

    }

    @Test
    void validCoreServiceTokenAuthenticatesInternalMemberRequest() throws Exception {
        given(memberInternalService.getMemberName(1L))
                .willReturn(new MemberInternalResult("홍길동", "010-1234-5678"));

        mockMvc.perform(get("/internal/v1/members/1")
                        .header(
                                ServiceAuthenticationHeaders.SERVICE_NAME,
                                ServiceAuthenticationHeaders.CORE_SERVICE)
                        .header(
                                ServiceAuthenticationHeaders.SERVICE_TOKEN,
                                "test-core-service-token"))
                .andExpect(status().isOk());
    }

    @Test
    void missingCoreServiceTokenRejectsInternalMemberRequest() throws Exception {
        mockMvc.perform(get("/internal/v1/members/1"))
                .andExpect(status().isUnauthorized());
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
