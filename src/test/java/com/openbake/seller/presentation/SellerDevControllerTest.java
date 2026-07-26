package com.openbake.seller.presentation;

import com.openbake.common.exception.EntityNotFoundException;
import com.openbake.member.domain.AccessTokenRepository;
import com.openbake.member.infrastructure.jwt.JwtTokenProvider;
import com.openbake.seller.application.SellerService;
import com.openbake.seller.presentation.dto.AccountVerificationCodeResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// SellerDevController가 @Profile({"local","dev"})라 해당 프로파일이 활성화되어야 빈이 등록됨
@WebMvcTest(SellerDevController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("local")
class SellerDevControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SellerService sellerService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private AccessTokenRepository accessTokenRepository;

    @Test
    @DisplayName("local/dev 프로파일에서는 목업 인증 코드를 조회할 수 있다")
    void getMockVerificationCode_success() throws Exception {
        given(sellerService.getMockVerificationCode("vr_1"))
                .willReturn(new AccountVerificationCodeResponse("vr_1", "1234", LocalDateTime.now().plusMinutes(3)));

        mockMvc.perform(get("/api/v1/sellers/settlement-account/verification-requests/vr_1/mock-code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("1234"));
    }

    @Test
    @DisplayName("존재하지 않는 인증 요청이면 404와 C003을 반환한다")
    void getMockVerificationCode_notFound() throws Exception {
        willThrow(new EntityNotFoundException("대상을 찾을 수 없습니다."))
                .given(sellerService).getMockVerificationCode("vr_1");

        mockMvc.perform(get("/api/v1/sellers/settlement-account/verification-requests/vr_1/mock-code"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("C003"));
    }
}
