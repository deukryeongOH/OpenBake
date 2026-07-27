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
import com.openbake.member.presentation.dto.auth.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberRepositoryImpl memberRepository;
    private final AuthCredentialRepositoryImpl authCredentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final OidcIdTokenVerifier oidcIdTokenVerifier;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenRepository accessTokenRepository;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (authCredentialRepository.existsByProviderAndEmail(AuthProvider.LOCAL, request.email())) {
            throw new DuplicateMemberException();
        }

        Member member = Member.create(request.name(), request.phoneNumber());
        Member savedMember = memberRepository.save(member);

        String encodedPassword = passwordEncoder.encode(request.password());
        AuthCredential authCredential = AuthCredential.createLocal(
                savedMember.getId(), request.email(), encodedPassword);
        authCredentialRepository.save(authCredential);

        return new SignupResponse(savedMember.getId(), request.email());
    }

    @Transactional
    public OAuthLoginResponse loginOrSignupWithOAuth(AuthProvider provider, OAuthLoginRequest request) {
        OidcIdentity identity = oidcIdTokenVerifier.verify(provider, request.idToken());

        Optional<AuthCredential> existing = authCredentialRepository
                .findByProviderAndProviderId(provider, identity.providerId());

        if (existing.isPresent()) {
            Member member = memberRepository.findById(existing.get().getMemberId())
                    .orElseThrow(() -> new EntityNotFoundException("연동된 회원 정보를 찾을 수 없습니다."));

            TokenPair tokens = issueTokens(member.getId(), member.getRole());

            return new OAuthLoginResponse(member.getId(), tokens.accessToken(), tokens.refreshToken(), identity.email(), member.getName(), false);
        }

        if (authCredentialRepository.existsByProviderAndEmail(AuthProvider.LOCAL, identity.email())) {
            throw new DuplicateMemberException();
        }

        Member savedMember = memberRepository.save(Member.createFromGoogle(identity.name()));
        authCredentialRepository.save(AuthCredential.createGoogle(
                savedMember.getId(), provider, identity.providerId(), identity.email()));

        TokenPair tokens = issueTokens(savedMember.getId(), savedMember.getRole());

        return new OAuthLoginResponse(savedMember.getId(), tokens.accessToken(), tokens.refreshToken(), identity.email(), savedMember.getName(), true);
    }

    @Transactional
    public LocalLoginResponse localLogin(LocalLoginRequest request) {
        AuthCredential authCredential = authCredentialRepository.findByProviderAndEmail(AuthProvider.LOCAL, request.email())
                .orElseThrow(() -> new AuthenticationFailedException("이메일 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.password(), authCredential.getPasswordHash())) {
            throw new AuthenticationFailedException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        Member member = memberRepository.findById(authCredential.getMemberId())
                .orElseThrow(() -> new EntityNotFoundException("연동된 회원 정보를 찾을 수 없습니다."));

        TokenPair tokens = issueTokens(member.getId(), member.getRole());

        return new LocalLoginResponse(member.getId(), tokens.accessToken(), tokens.refreshToken(), member.getRole());
    }

    public ReissueResponse reissue(ReissueRequest request) {
        if (!jwtTokenProvider.isValid(request.refreshToken())) {
            throw new InvalidRefreshTokenException("유효하지 않은 리프레시 토큰입니다.");
        }

        Long memberId = jwtTokenProvider.getMemberId(request.refreshToken());

        String storedRefreshToken = refreshTokenRepository.findByMemberId(memberId)
                .orElseThrow(() -> new InvalidRefreshTokenException("유효하지 않은 리프레시 토큰입니다."));

        if (!storedRefreshToken.equals(request.refreshToken())) {
            refreshTokenRepository.deleteByMemberId(memberId);
            throw new InvalidRefreshTokenException("이미 사용된 리프레시 토큰입니다. 다시 로그인해주세요.");
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new EntityNotFoundException("연동된 회원 정보를 찾을 수 없습니다."));

        TokenPair tokens = issueTokens(member.getId(), member.getRole());

        return new ReissueResponse(tokens.accessToken(), tokens.refreshToken());
    }

    public void logout(LogoutRequest request) {
        if (!jwtTokenProvider.isValid(request.refreshToken())) {
            throw new InvalidRefreshTokenException("유효하지 않은 리프레시 토큰입니다.");
        }

        Long memberId = jwtTokenProvider.getMemberId(request.refreshToken());

        refreshTokenRepository.deleteByMemberId(memberId);
    }

    private TokenPair issueTokens(Long memberId, Role role) {
        String accessToken = jwtTokenProvider.createAccessToken(memberId, role);
        String refreshToken = jwtTokenProvider.createRefreshToken(memberId);

        accessTokenRepository.save(memberId, accessToken);
        refreshTokenRepository.save(memberId, refreshToken);

        return new TokenPair(accessToken, refreshToken);
    }

    private record TokenPair(String accessToken, String refreshToken) {}

}
