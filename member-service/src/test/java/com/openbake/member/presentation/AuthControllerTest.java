package com.openbake.member.presentation;

import com.openbake.common.exception.AuthenticationFailedException;
import com.openbake.common.exception.DuplicateMemberException;
import com.openbake.common.exception.InvalidIdTokenException;
import com.openbake.common.exception.InvalidRefreshTokenException;
import com.openbake.member.application.AuthService;
import com.openbake.member.domain.AccessTokenRepository;
import com.openbake.member.domain.AuthProvider;
import com.openbake.member.domain.Role;
import com.openbake.member.infrastructure.jwt.JwtTokenProvider;
import com.openbake.member.presentation.dto.auth.LocalLoginRequest;
import com.openbake.member.presentation.dto.auth.LocalLoginResponse;
import com.openbake.member.presentation.dto.auth.LogoutRequest;
import com.openbake.member.presentation.dto.auth.OAuthLoginRequest;
import com.openbake.member.presentation.dto.auth.OAuthLoginResponse;
import com.openbake.member.presentation.dto.auth.ReissueRequest;
import com.openbake.member.presentation.dto.auth.ReissueResponse;
import com.openbake.member.presentation.dto.auth.SignupRequest;
import com.openbake.member.presentation.dto.auth.SignupResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private AccessTokenRepository accessTokenRepository;

    @Test
    @DisplayName("회원가입 성공 시 200과 회원 정보를 반환한다")
    void signup_success() throws Exception {
        SignupRequest request = new SignupRequest("test@example.com", "password123", "홍길동", "010-1234-5678");
        given(authService.signup(any())).willReturn(new SignupResponse(1L, "test@example.com"));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.memberId").value(1))
                .andExpect(jsonPath("$.data.email").value("test@example.com"));
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 409와 에러 응답을 반환한다")
    void signup_duplicateEmail() throws Exception {
        SignupRequest request = new SignupRequest("test@example.com", "password123", "홍길동", "010-1234-5678");
        given(authService.signup(any())).willThrow(new DuplicateMemberException());

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ME001"));
    }

    @Test
    @DisplayName("이메일 형식이 올바르지 않으면 400과 에러 응답을 반환한다")
    void signup_invalidEmail() throws Exception {
        SignupRequest request = new SignupRequest("invalid-email", "password123", "홍길동", "010-1234-5678");

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C001"));
    }

    @Test
    @DisplayName("필수 값이 비어있으면 400과 에러 응답을 반환한다")
    void signup_blankName() throws Exception {
        SignupRequest request = new SignupRequest("test@example.com", "password123", "", "010-1234-5678");

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C001"));
    }

    @Test
    @DisplayName("Google 로그인 성공 시 200과 회원 정보, 토큰을 반환한다")
    void loginWithOAuth_google_success() throws Exception {
        OAuthLoginRequest request = new OAuthLoginRequest("valid-id-token");
        given(authService.loginOrSignupWithOAuth(eq(AuthProvider.GOOGLE), any()))
                .willReturn(new OAuthLoginResponse(1L, "access-token", "refresh-token", "test@example.com", "홍길동", true));

        mockMvc.perform(post("/api/v1/auth/oauth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.memberId").value(1))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.newMember").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"));
    }

    @Test
    @DisplayName("idToken이 비어있으면 400과 에러 응답을 반환한다")
    void loginWithOAuth_blankIdToken() throws Exception {
        OAuthLoginRequest request = new OAuthLoginRequest("");

        mockMvc.perform(post("/api/v1/auth/oauth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C001"));
    }

    @Test
    @DisplayName("같은 이메일의 LOCAL 계정이 있으면 409와 에러 응답을 반환한다")
    void loginWithOAuth_duplicateLocalEmail() throws Exception {
        OAuthLoginRequest request = new OAuthLoginRequest("valid-id-token");
        given(authService.loginOrSignupWithOAuth(eq(AuthProvider.GOOGLE), any()))
                .willThrow(new DuplicateMemberException());

        mockMvc.perform(post("/api/v1/auth/oauth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ME001"));
    }

    @Test
    @DisplayName("ID 토큰 검증에 실패하면 401과 에러 응답을 반환한다")
    void loginWithOAuth_invalidToken() throws Exception {
        OAuthLoginRequest request = new OAuthLoginRequest("invalid-id-token");
        given(authService.loginOrSignupWithOAuth(eq(AuthProvider.GOOGLE), any()))
                .willThrow(new InvalidIdTokenException());

        mockMvc.perform(post("/api/v1/auth/oauth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ME002"));
    }

    @Test
    @DisplayName("지원하지 않는 provider 경로면 400과 에러 응답을 반환한다")
    void loginWithOAuth_unknownProvider() throws Exception {
        OAuthLoginRequest request = new OAuthLoginRequest("valid-id-token");

        mockMvc.perform(post("/api/v1/auth/oauth/facebook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C001"));
    }

    @Test
    @DisplayName("로그인 성공 시 200과 회원 정보, 토큰을 반환한다")
    void login_success() throws Exception {
        LocalLoginRequest request = new LocalLoginRequest("test@example.com", "password123");
        given(authService.localLogin(any()))
                .willReturn(new LocalLoginResponse(1L, "access-token", "refresh-token", Role.CUSTOMER));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.memberId").value(1))
                .andExpect(jsonPath("$.data.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"));
    }

    @Test
    @DisplayName("이메일 또는 비밀번호가 일치하지 않으면 401과 에러 응답을 반환한다")
    void login_authenticationFailed() throws Exception {
        LocalLoginRequest request = new LocalLoginRequest("test@example.com", "wrongPassword");
        given(authService.localLogin(any())).willThrow(new AuthenticationFailedException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ME003"));
    }

    @Test
    @DisplayName("이메일 형식이 올바르지 않으면 400과 에러 응답을 반환한다")
    void login_invalidEmail() throws Exception {
        LocalLoginRequest request = new LocalLoginRequest("invalid-email", "password123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C001"));
    }

    @Test
    @DisplayName("비밀번호가 비어있으면 400과 에러 응답을 반환한다")
    void login_blankPassword() throws Exception {
        LocalLoginRequest request = new LocalLoginRequest("test@example.com", "");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C001"));
    }

    @Test
    @DisplayName("재발급 성공 시 200과 새 토큰을 반환한다")
    void reissue_success() throws Exception {
        ReissueRequest request = new ReissueRequest("old-refresh-token");
        given(authService.reissue(any()))
                .willReturn(new ReissueResponse("new-access-token", "new-refresh-token"));

        mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh-token"));
    }

    @Test
    @DisplayName("refreshToken이 유효하지 않으면 401과 에러 응답을 반환한다")
    void reissue_invalidToken() throws Exception {
        ReissueRequest request = new ReissueRequest("invalid-refresh-token");
        given(authService.reissue(any())).willThrow(new InvalidRefreshTokenException());

        mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ME002"));
    }

    @Test
    @DisplayName("refreshToken이 비어있으면 400과 에러 응답을 반환한다")
    void reissue_blankRefreshToken() throws Exception {
        ReissueRequest request = new ReissueRequest("");

        mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C001"));
    }

    @Test
    @DisplayName("로그아웃 성공 시 200을 반환한다")
    void logout_success() throws Exception {
        LogoutRequest request = new LogoutRequest("valid-refresh-token");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("refreshToken이 유효하지 않으면 401과 에러 응답을 반환한다")
    void logout_invalidToken() throws Exception {
        LogoutRequest request = new LogoutRequest("invalid-refresh-token");
        willThrow(new InvalidRefreshTokenException()).given(authService).logout(any());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ME002"));
    }

    @Test
    @DisplayName("refreshToken이 비어있으면 400과 에러 응답을 반환한다")
    void logout_blankRefreshToken() throws Exception {
        LogoutRequest request = new LogoutRequest("");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C001"));
    }
}
