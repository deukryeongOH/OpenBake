package com.openbake.member.application;

import com.openbake.common.exception.AccessDeniedException;
import com.openbake.common.exception.AuthenticationFailedException;
import com.openbake.common.exception.EntityNotFoundException;
import com.openbake.common.security.Authorities;
import com.openbake.common.security.CurrentMemberProvider;
import com.openbake.common.security.jwt.AccessTokenRepository;
import com.openbake.member.domain.*;
import com.openbake.member.application.dto.member.MemberResult;
import com.openbake.member.application.dto.member.MemberUpdateCommand;
import com.openbake.member.application.dto.member.MemberUpdateResult;
import com.openbake.member.application.dto.member.PasswordChangeCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final AuthCredentialRepository authCredentialRepository;
    private final CurrentMemberProvider currentMemberProvider;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenRepository accessTokenRepository;

    @Transactional(readOnly = true)
    public MemberResult getMemberById(Long id) {
        if (!currentMemberProvider.hasAuthority(Authorities.ROLE_ADMIN) && !currentMemberProvider.getId().equals(id)) {
            throw new AccessDeniedException();
        }

        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("대상을 찾을 수 없습니다."));

        AuthCredential authCredential = authCredentialRepository.findByMemberId(id)
                .orElseThrow(() -> new EntityNotFoundException("대상을 찾을 수 없습니다."));

        return MemberResult.from(member, authCredential.getEmail());
    }

    @Transactional
    public MemberUpdateResult updateMember(Long id, MemberUpdateCommand command) {
        if (!currentMemberProvider.getId().equals(id)) {
            throw new AccessDeniedException();
        }

        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("대상을 찾을 수 없습니다."));

        member.updateMember(command.name(), command.phoneNumber());

        return new MemberUpdateResult(id, member.getName(), member.getPhoneNumber());
    }

    @Transactional
    public void changePassword(Long id, PasswordChangeCommand command) {
        if (!currentMemberProvider.getId().equals(id)) {
            throw new AccessDeniedException();
        }

        AuthCredential authCredential = authCredentialRepository.findByMemberId(id)
                .orElseThrow(() -> new EntityNotFoundException("대상을 찾을 수 없습니다."));

        if (!authCredential.getProvider().equals(AuthProvider.LOCAL)) {
            throw new AccessDeniedException();
        }

        if (!passwordEncoder.matches(command.currentPassword(), authCredential.getPasswordHash())) {
            throw new AuthenticationFailedException("비밀번호가 일치하지 않습니다.");
        }

        authCredential.changePassword(passwordEncoder.encode(command.newPassword()));
    }

    @Transactional
    public void withdrawMember(Long id) {
        if (!currentMemberProvider.getId().equals(id)) {
            throw new AccessDeniedException();
        }

        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("대상을 찾을 수 없습니다."));

        if (member.getStatus().equals(MemberStatus.WITHDRAWN)) {
            throw new IllegalStateException("이미 탈퇴 처리된 회원입니다.");
        }

        AuthCredential authCredential = authCredentialRepository.findByMemberId(id)
                .orElseThrow(() -> new EntityNotFoundException("대상을 찾을 수 없습니다."));

        member.withdraw();
        authCredential.withdraw();
        refreshTokenRepository.deleteByMemberId(id);
        accessTokenRepository.blacklistByMemberId(id);
    }

}
