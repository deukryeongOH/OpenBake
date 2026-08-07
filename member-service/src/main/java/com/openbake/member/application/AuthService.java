package com.openbake.member.application;

import com.openbake.common.exception.AuthenticationFailedException;
import com.openbake.common.exception.DuplicateMemberException;
import com.openbake.common.exception.EntityNotFoundException;
import com.openbake.common.exception.InvalidRefreshTokenException;
import com.openbake.member.application.dto.auth.LocalLoginCommand;
import com.openbake.member.application.dto.auth.LocalLoginResult;
import com.openbake.member.application.dto.auth.LogoutCommand;
import com.openbake.member.application.dto.auth.OAuthLoginCommand;
import com.openbake.member.application.dto.auth.OAuthLoginResult;
import com.openbake.member.application.dto.auth.ReissueCommand;
import com.openbake.member.application.dto.auth.ReissueResult;
import com.openbake.member.application.dto.auth.SignupCommand;
import com.openbake.member.application.dto.auth.SignupResult;
import com.openbake.member.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberRepository memberRepository;
    private final AuthCredentialRepository authCredentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final IdTokenVerifier oidcIdTokenVerifier;
    private final TokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenRepository accessTokenRepository;

    @Transactional
    public SignupResult signup(SignupCommand request) {
        if (authCredentialRepository.existsByProviderAndEmail(AuthProvider.LOCAL, request.email())) {
            throw new DuplicateMemberException();
        }

        Member member = Member.create(request.name(), request.phoneNumber());
        Member savedMember = memberRepository.save(member);

        String encodedPassword = passwordEncoder.encode(request.password());
        AuthCredential authCredential = AuthCredential.createLocal(
                savedMember.getId(), request.email(), encodedPassword);
        authCredentialRepository.save(authCredential);

        return new SignupResult(savedMember.getId(), request.email());
    }

    @Transactional
    public OAuthLoginResult loginOrSignupWithOAuth(OAuthLoginCommand command) {
        OidcIdentity identity = oidcIdTokenVerifier.verify(command.provider(), command.idToken());

        Optional<AuthCredential> existing = authCredentialRepository
                .findByProviderAndProviderId(command.provider(), identity.providerId());

        if (existing.isPresent()) {
            Member member = memberRepository.findById(existing.get().getMemberId())
                    .orElseThrow(() -> new EntityNotFoundException("연동된 회원 정보를 찾을 수 없습니다."));

            TokenPair tokens = issueTokens(member.getId(), member.getRole());

            return new OAuthLoginResult(member.getId(), tokens.accessToken(), tokens.refreshToken(), identity.email(), member.getName(), false);
        }

        if (authCredentialRepository.existsByProviderAndEmail(AuthProvider.LOCAL, identity.email())) {
            throw new DuplicateMemberException();
        }

        Member savedMember = memberRepository.save(Member.createFromGoogle(identity.name()));
        authCredentialRepository.save(AuthCredential.createGoogle(
                savedMember.getId(), command.provider(), identity.providerId(), identity.email()));

        TokenPair tokens = issueTokens(savedMember.getId(), savedMember.getRole());

        return new OAuthLoginResult(savedMember.getId(), tokens.accessToken(), tokens.refreshToken(), identity.email(), savedMember.getName(), true);
    }

    @Transactional
    public LocalLoginResult localLogin(LocalLoginCommand command) {
        AuthCredential authCredential = authCredentialRepository.findByProviderAndEmail(AuthProvider.LOCAL, command.email())
                .orElseThrow(() -> new AuthenticationFailedException("이메일 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(command.password(), authCredential.getPasswordHash())) {
            throw new AuthenticationFailedException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        Member member = memberRepository.findById(authCredential.getMemberId())
                .orElseThrow(() -> new EntityNotFoundException("연동된 회원 정보를 찾을 수 없습니다."));

        TokenPair tokens = issueTokens(member.getId(), member.getRole());

        return new LocalLoginResult(member.getId(), tokens.accessToken(), tokens.refreshToken(), member.getRole());
    }

    public ReissueResult reissue(ReissueCommand command) {
        if (!jwtTokenProvider.isValid(command.refreshToken())) {
            throw new InvalidRefreshTokenException("유효하지 않은 리프레시 토큰입니다.");
        }

        Long memberId = jwtTokenProvider.getMemberId(command.refreshToken());

        String storedRefreshToken = refreshTokenRepository.findByMemberId(memberId)
                .orElseThrow(() -> new InvalidRefreshTokenException("유효하지 않은 리프레시 토큰입니다."));

        if (!storedRefreshToken.equals(command.refreshToken())) {
            refreshTokenRepository.deleteByMemberId(memberId);
            throw new InvalidRefreshTokenException("이미 사용된 리프레시 토큰입니다. 다시 로그인해주세요.");
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new EntityNotFoundException("연동된 회원 정보를 찾을 수 없습니다."));

        TokenPair tokens = issueTokens(member.getId(), member.getRole());

        return new ReissueResult(tokens.accessToken(), tokens.refreshToken());
    }

    public void logout(LogoutCommand command) {
        if (!jwtTokenProvider.isValid(command.refreshToken())) {
            throw new InvalidRefreshTokenException("유효하지 않은 리프레시 토큰입니다.");
        }

        Long memberId = jwtTokenProvider.getMemberId(command.refreshToken());

        refreshTokenRepository.deleteByMemberId(memberId);
        accessTokenRepository.blacklistByMemberId(memberId);
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
