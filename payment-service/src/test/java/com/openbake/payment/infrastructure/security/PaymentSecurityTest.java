package com.openbake.payment.infrastructure.security;

import com.openbake.common.security.CurrentMemberProvider;
import com.openbake.common.security.gateway.GatewayIdentityHeaders;
import com.openbake.common.security.service.ServiceAuthenticationHeaders;
import com.openbake.payment.application.DepositService;
import com.openbake.payment.application.PaymentService;
import com.openbake.payment.application.dto.DepositResult;
import com.openbake.payment.presentation.DepositController;
import com.openbake.payment.presentation.internal.PaymentInternalController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {DepositController.class, PaymentInternalController.class})
@Import({PaymentSecurityConfig.class, CurrentMemberProvider.class})
@ImportAutoConfiguration(ServletWebSecurityAutoConfiguration.class)
@TestPropertySource(properties = "CORE_SERVICE_TOKEN=test-core-service-token")
class PaymentSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DepositService depositService;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void validGatewayHeadersAuthenticateProtectedRequest() throws Exception {
        given(depositService.getBalance(1L)).willReturn(depositResult());

        mockMvc.perform(get("/api/v1/deposit/account")
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
        mockMvc.perform(get("/api/v1/deposit/account")
                        .header("Authorization", "Bearer valid-access-token"))
                .andExpect(status().isUnauthorized());

    }

    @Test
    void validCoreServiceTokenAuthenticatesInternalPaymentRequest() throws Exception {
        given(depositService.getBalance(1L)).willReturn(depositResult());

        mockMvc.perform(get("/internal/v1/deposits/1/balance")
                        .header(
                                ServiceAuthenticationHeaders.SERVICE_NAME,
                                ServiceAuthenticationHeaders.CORE_SERVICE)
                        .header(
                                ServiceAuthenticationHeaders.SERVICE_TOKEN,
                                "test-core-service-token"))
                .andExpect(status().isOk());
    }

    @Test
    void missingCoreServiceTokenRejectsInternalPaymentRequest() throws Exception {
        mockMvc.perform(get("/internal/v1/deposits/1/balance"))
                .andExpect(status().isUnauthorized());
    }

    private DepositResult depositResult() {
        return new DepositResult(1L, BigDecimal.ZERO, false);
    }
}
