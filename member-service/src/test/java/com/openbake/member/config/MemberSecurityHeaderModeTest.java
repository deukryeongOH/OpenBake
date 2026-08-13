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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = MemberController.class,
        properties = "openbake.security.auth-mode=header"
)
@Import(SecurityConfig.class)
@ImportAutoConfiguration(ServletWebSecurityAutoConfiguration.class)
class MemberSecurityHeaderModeTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private AccessTokenRepository accessTokenRepository;

    @Test
    void validGatewayHeadersAuthenticateProtectedRequestWithoutJwtValidation() throws Exception {
        given(memberService.getMemberById(1L)).willReturn(memberResult());

        mockMvc.perform(get("/api/v1/members/1")
                        .header(GatewayIdentityHeaders.MEMBER_ID, "1")
                        .header(GatewayIdentityHeaders.MEMBER_ROLE, "CUSTOMER")
                        .header(
                                GatewayIdentityHeaders.AUTH_SOURCE,
                                GatewayIdentityHeaders.EXPECTED_AUTH_SOURCE
                        ))
                .andExpect(status().isOk());

        verifyNoInteractions(jwtTokenProvider, accessTokenRepository);
    }

    @Test
    void bearerTokenWithoutGatewayHeadersIsRejectedWithoutJwtValidation() throws Exception {
        mockMvc.perform(get("/api/v1/members/1")
                        .header("Authorization", "Bearer valid-access-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(jwtTokenProvider, accessTokenRepository);
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
