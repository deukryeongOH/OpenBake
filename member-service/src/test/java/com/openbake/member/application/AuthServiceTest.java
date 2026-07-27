package com.openbake.member.application;

import com.openbake.common.exception.AuthenticationFailedException;
import com.openbake.common.exception.DuplicateMemberException;
import com.openbake.common.exception.EntityNotFoundException;
import com.openbake.common.exception.InvalidRefreshTokenException;
import com.openbake.member.domain.AccessTokenRepository;
import com.openbake.member.domain.AuthCredential;
import com.openbake.member.domain.AuthProvider;
import com.openbake.member.domain.Member;
import com.openbake.member.domain.RefreshTokenRepository;
import com.openbake.member.domain.Role;
import com.openbake.member.infrastructure.AuthCredentialRepositoryImpl;
import com.openbake.member.infrastructure.MemberRepositoryImpl;
import com.openbake.member.infrastructure.jwt.JwtTokenProvider;
import com.openbake.member.infrastructure.oauth.OidcIdTokenVerifier;
import com.openbake.member.infrastructure.oauth.OidcIdentity;
import com.openbake.member.presentation.dto.auth.LocalLoginRequest;
import com.openbake.member.presentation.dto.auth.LocalLoginResponse;
import com.openbake.member.presentation.dto.auth.LogoutRequest;
import com.openbake.member.presentation.dto.auth.OAuthLoginRequest;
import com.openbake.member.presentation.dto.auth.OAuthLoginResponse;
import com.openbake.member.presentation.dto.auth.ReissueRequest;
import com.openbake.member.presentation.dto.auth.ReissueResponse;
import com.openbake.member.presentation.dto.auth.SignupRequest;
import com.openbake.member.presentation.dto.auth.SignupResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private MemberRepositoryImpl memberRepository;

    @Mock
    private AuthCredentialRepositoryImpl authCredentialRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private OidcIdTokenVerifier oidcIdTokenVerifier;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AccessTokenRepository accessTokenRepository;

    @InjectMocks
    private AuthService authService;

    private SignupRequest request;

    @BeforeEach
    void setUp() {
        request = new SignupRequest("test@example.com", "password123", "홍길동", "010-1234-5678");
    }

    @Test
    @DisplayName("회원가입에 성공하면 회원과 인증 정보를 저장하고 응답을 반환한다")
    void signup_success() {
        given(authCredentialRepository.existsByProviderAndEmail(AuthProvider.LOCAL, request.email()))
                .willReturn(false);

        Member savedMember = Member.create(request.name(), request.phoneNumber());
        ReflectionTestUtils.setField(savedMember, "id", 1L);
        given(memberRepository.save(any(Member.class))).willReturn(savedMember);

        given(passwordEncoder.encode(request.password())).willReturn("encodedPassword");
        given(authCredentialRepository.save(any(AuthCredential.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        SignupResponse response = authService.signup(request);

        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo(request.email());

        ArgumentCaptor<AuthCredential> captor = ArgumentCaptor.forClass(AuthCredential.class);
        verify(authCredentialRepository).save(captor.capture());
        AuthCredential savedCredential = captor.getValue();
        assertThat(savedCredential.getMemberId()).isEqualTo(1L);
        assertThat(savedCredential.getProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(savedCredential.getEmail()).isEqualTo(request.email());
        assertThat(savedCredential.getPasswordHash()).isEqualTo("encodedPassword");
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 DuplicateMemberException을 던지고 저장하지 않는다")
    void signup_duplicateEmail_throwsException() {
        given(authCredentialRepository.existsByProviderAndEmail(AuthProvider.LOCAL, request.email()))
                .willReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(DuplicateMemberException.class);

        verify(memberRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
        verify(authCredentialRepository, never()).save(any());
    }

    @Test
    @DisplayName("신규 이메일로 Google 로그인하면 회원과 인증 정보를 새로 만들고 newMember=true를 반환한다")
    void loginOrSignupWithOAuth_newMember_createsAccount() {
        OAuthLoginRequest request = new OAuthLoginRequest("id-token");
        OidcIdentity identity = new OidcIdentity("google-sub-1", "new@example.com", "홍길동");
        given(oidcIdTokenVerifier.verify(AuthProvider.GOOGLE, "id-token")).willReturn(identity);

        given(authCredentialRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-sub-1"))
                .willReturn(Optional.empty());
        given(authCredentialRepository.existsByProviderAndEmail(AuthProvider.LOCAL, "new@example.com"))
                .willReturn(false);

        Member savedMember = Member.createFromGoogle("홍길동");
        ReflectionTestUtils.setField(savedMember, "id", 2L);
        given(memberRepository.save(any(Member.class))).willReturn(savedMember);
        given(authCredentialRepository.save(any(AuthCredential.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        given(jwtTokenProvider.createAccessToken(2L, Role.CUSTOMER)).willReturn("access-token");
        given(jwtTokenProvider.createRefreshToken(2L)).willReturn("refresh-token");

        OAuthLoginResponse response = authService.loginOrSignupWithOAuth(AuthProvider.GOOGLE, request);

        assertThat(response.memberId()).isEqualTo(2L);
        assertThat(response.email()).isEqualTo("new@example.com");
        assertThat(response.name()).isEqualTo("홍길동");
        assertThat(response.newMember()).isTrue();
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");

        ArgumentCaptor<AuthCredential> captor = ArgumentCaptor.forClass(AuthCredential.class);
        verify(authCredentialRepository).save(captor.capture());
        AuthCredential savedCredential = captor.getValue();
        assertThat(savedCredential.getMemberId()).isEqualTo(2L);
        assertThat(savedCredential.getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(savedCredential.getProviderId()).isEqualTo("google-sub-1");
        assertThat(savedCredential.getEmail()).isEqualTo("new@example.com");

        verify(accessTokenRepository).save(2L, "access-token");
        verify(refreshTokenRepository).save(2L, "refresh-token");
    }

    @Test
    @DisplayName("이미 GOOGLE로 가입된 사용자가 다시 로그인하면 새로 만들지 않고 기존 회원 정보를 반환한다")
    void loginOrSignupWithOAuth_existingMember_returnsLogin() {
        OAuthLoginRequest request = new OAuthLoginRequest("id-token");
        OidcIdentity identity = new OidcIdentity("google-sub-2", "exist@example.com", "기존회원");
        given(oidcIdTokenVerifier.verify(AuthProvider.GOOGLE, "id-token")).willReturn(identity);

        AuthCredential existingCredential = AuthCredential.createGoogle(
                5L, AuthProvider.GOOGLE, "google-sub-2", "exist@example.com");
        given(authCredentialRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-sub-2"))
                .willReturn(Optional.of(existingCredential));

        Member existingMember = Member.create("기존회원", "010-0000-0000");
        ReflectionTestUtils.setField(existingMember, "id", 5L);
        given(memberRepository.findById(5L)).willReturn(Optional.of(existingMember));

        given(jwtTokenProvider.createAccessToken(5L, Role.CUSTOMER)).willReturn("access-token");
        given(jwtTokenProvider.createRefreshToken(5L)).willReturn("refresh-token");

        OAuthLoginResponse response = authService.loginOrSignupWithOAuth(AuthProvider.GOOGLE, request);

        assertThat(response.memberId()).isEqualTo(5L);
        assertThat(response.name()).isEqualTo("기존회원");
        assertThat(response.newMember()).isFalse();
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");

        verify(memberRepository, never()).save(any());
        verify(authCredentialRepository, never()).save(any());
        verify(accessTokenRepository).save(5L, "access-token");
        verify(refreshTokenRepository).save(5L, "refresh-token");
    }

    @Test
    @DisplayName("같은 이메일의 LOCAL 계정이 이미 있으면 DuplicateMemberException을 던지고 저장하지 않는다")
    void loginOrSignupWithOAuth_localEmailConflict_throwsException() {
        OAuthLoginRequest request = new OAuthLoginRequest("id-token");
        OidcIdentity identity = new OidcIdentity("google-sub-3", "local@example.com", "이름");
        given(oidcIdTokenVerifier.verify(AuthProvider.GOOGLE, "id-token")).willReturn(identity);

        given(authCredentialRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-sub-3"))
                .willReturn(Optional.empty());
        given(authCredentialRepository.existsByProviderAndEmail(AuthProvider.LOCAL, "local@example.com"))
                .willReturn(true);

        assertThatThrownBy(() -> authService.loginOrSignupWithOAuth(AuthProvider.GOOGLE, request))
                .isInstanceOf(DuplicateMemberException.class);

        verify(memberRepository, never()).save(any());
        verify(authCredentialRepository, never()).save(any());
    }

    @Test
    @DisplayName("이메일/비밀번호가 일치하면 토큰을 발급하고 회원 정보를 반환한다")
    void localLogin_success() {
        LocalLoginRequest request = new LocalLoginRequest("test@example.com", "password123");

        AuthCredential authCredential = AuthCredential.createLocal(1L, "test@example.com", "encodedPassword");
        given(authCredentialRepository.findByProviderAndEmail(AuthProvider.LOCAL, "test@example.com"))
                .willReturn(Optional.of(authCredential));
        given(passwordEncoder.matches("password123", "encodedPassword")).willReturn(true);

        Member member = Member.create("홍길동", "010-1234-5678");
        ReflectionTestUtils.setField(member, "id", 1L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        given(jwtTokenProvider.createAccessToken(1L, Role.CUSTOMER)).willReturn("access-token");
        given(jwtTokenProvider.createRefreshToken(1L)).willReturn("refresh-token");

        LocalLoginResponse response = authService.localLogin(request);

        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.role()).isEqualTo(Role.CUSTOMER);
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");

        verify(accessTokenRepository).save(1L, "access-token");
        verify(refreshTokenRepository).save(1L, "refresh-token");
    }

    @Test
    @DisplayName("가입되지 않은 이메일이면 AuthenticationFailedException을 던진다")
    void localLogin_emailNotFound_throwsException() {
        LocalLoginRequest request = new LocalLoginRequest("unknown@example.com", "password123");

        given(authCredentialRepository.findByProviderAndEmail(AuthProvider.LOCAL, "unknown@example.com"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.localLogin(request))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 AuthenticationFailedException을 던진다")
    void localLogin_wrongPassword_throwsException() {
        LocalLoginRequest request = new LocalLoginRequest("test@example.com", "wrongPassword");

        AuthCredential authCredential = AuthCredential.createLocal(1L, "test@example.com", "encodedPassword");
        given(authCredentialRepository.findByProviderAndEmail(AuthProvider.LOCAL, "test@example.com"))
                .willReturn(Optional.of(authCredential));
        given(passwordEncoder.matches("wrongPassword", "encodedPassword")).willReturn(false);

        assertThatThrownBy(() -> authService.localLogin(request))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(memberRepository, never()).findById(any());
    }

    @Test
    @DisplayName("인증 정보는 있는데 연동된 회원이 없으면 EntityNotFoundException을 던진다")
    void localLogin_memberNotFound_throwsException() {
        LocalLoginRequest request = new LocalLoginRequest("test@example.com", "password123");

        AuthCredential authCredential = AuthCredential.createLocal(1L, "test@example.com", "encodedPassword");
        given(authCredentialRepository.findByProviderAndEmail(AuthProvider.LOCAL, "test@example.com"))
                .willReturn(Optional.of(authCredential));
        given(passwordEncoder.matches("password123", "encodedPassword")).willReturn(true);
        given(memberRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.localLogin(request))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("유효한 refreshToken이 저장된 값과 일치하면 새 토큰 쌍을 발급하고 Redis 값을 교체한다")
    void reissue_success() {
        ReissueRequest request = new ReissueRequest("old-refresh-token");

        given(jwtTokenProvider.isValid("old-refresh-token")).willReturn(true);
        given(jwtTokenProvider.getMemberId("old-refresh-token")).willReturn(1L);
        given(refreshTokenRepository.findByMemberId(1L)).willReturn(Optional.of("old-refresh-token"));

        Member member = Member.create("홍길동", "010-1234-5678");
        ReflectionTestUtils.setField(member, "id", 1L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        given(jwtTokenProvider.createAccessToken(1L, Role.CUSTOMER)).willReturn("new-access-token");
        given(jwtTokenProvider.createRefreshToken(1L)).willReturn("new-refresh-token");

        ReissueResponse response = authService.reissue(request);

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");

        verify(accessTokenRepository).save(1L, "new-access-token");
        verify(refreshTokenRepository).save(1L, "new-refresh-token");
    }

    @Test
    @DisplayName("서명/만료가 유효하지 않은 refreshToken이면 InvalidRefreshTokenException을 던진다")
    void reissue_invalidToken_throwsException() {
        ReissueRequest request = new ReissueRequest("broken-token");

        given(jwtTokenProvider.isValid("broken-token")).willReturn(false);

        assertThatThrownBy(() -> authService.reissue(request))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository, never()).findByMemberId(any());
    }

    @Test
    @DisplayName("Redis에 저장된 refreshToken이 없으면 InvalidRefreshTokenException을 던진다")
    void reissue_noStoredToken_throwsException() {
        ReissueRequest request = new ReissueRequest("some-refresh-token");

        given(jwtTokenProvider.isValid("some-refresh-token")).willReturn(true);
        given(jwtTokenProvider.getMemberId("some-refresh-token")).willReturn(1L);
        given(refreshTokenRepository.findByMemberId(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.reissue(request))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(memberRepository, never()).findById(any());
    }

    @Test
    @DisplayName("요청 온 refreshToken이 저장된 값과 다르면(재사용 의심) InvalidRefreshTokenException을 던지고 세션을 무효화한다")
    void reissue_tokenMismatch_throwsExceptionAndInvalidatesSession() {
        ReissueRequest request = new ReissueRequest("stale-refresh-token");

        given(jwtTokenProvider.isValid("stale-refresh-token")).willReturn(true);
        given(jwtTokenProvider.getMemberId("stale-refresh-token")).willReturn(1L);
        given(refreshTokenRepository.findByMemberId(1L)).willReturn(Optional.of("latest-refresh-token"));

        assertThatThrownBy(() -> authService.reissue(request))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(memberRepository, never()).findById(any());
        verify(refreshTokenRepository).deleteByMemberId(1L);
    }

    @Test
    @DisplayName("유효한 refreshToken으로 로그아웃하면 저장된 refreshToken을 삭제한다")
    void logout_success() {
        LogoutRequest request = new LogoutRequest("valid-refresh-token");

        given(jwtTokenProvider.isValid("valid-refresh-token")).willReturn(true);
        given(jwtTokenProvider.getMemberId("valid-refresh-token")).willReturn(1L);

        authService.logout(request);

        verify(refreshTokenRepository).deleteByMemberId(1L);
    }

    @Test
    @DisplayName("유효하지 않은 refreshToken으로 로그아웃하면 InvalidRefreshTokenException을 던진다")
    void logout_invalidToken_throwsException() {
        LogoutRequest request = new LogoutRequest("broken-token");

        given(jwtTokenProvider.isValid("broken-token")).willReturn(false);

        assertThatThrownBy(() -> authService.logout(request))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository, never()).deleteByMemberId(any());
    }
}
