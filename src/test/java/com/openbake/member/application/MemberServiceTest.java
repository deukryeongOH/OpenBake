package com.openbake.member.application;

import com.openbake.common.exception.AccessDeniedException;
import com.openbake.common.exception.AuthenticationFailedException;
import com.openbake.common.exception.EntityNotFoundException;
import com.openbake.common.security.CurrentMemberProvider;
import com.openbake.member.domain.AccessTokenRepository;
import com.openbake.member.domain.AuthCredential;
import com.openbake.member.domain.AuthProvider;
import com.openbake.member.domain.Member;
import com.openbake.member.domain.MemberStatus;
import com.openbake.member.domain.RefreshTokenRepository;
import com.openbake.member.domain.Role;
import com.openbake.member.infrastructure.AuthCredentialRepositoryImpl;
import com.openbake.member.infrastructure.MemberRepositoryImpl;
import com.openbake.member.presentation.dto.member.MemberResponse;
import com.openbake.member.presentation.dto.member.MemberUpdateRequest;
import com.openbake.member.presentation.dto.member.MemberUpdateResponse;
import com.openbake.member.presentation.dto.member.PasswordChangeRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class MemberServiceTest {

    @Mock
    private MemberRepositoryImpl memberRepository;

    @Mock
    private AuthCredentialRepositoryImpl authCredentialRepository;

    @Mock
    private CurrentMemberProvider currentMemberProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AccessTokenRepository accessTokenRepository;

    @InjectMocks
    private MemberService memberService;

    @Test
    @DisplayName("본인이 조회하면 회원 정보를 반환한다")
    void getMemberById_self_success() {
        given(currentMemberProvider.hasRole(Role.ADMIN)).willReturn(false);
        given(currentMemberProvider.getId()).willReturn(1L);

        Member member = Member.create("홍길동", "010-1234-5678");
        ReflectionTestUtils.setField(member, "id", 1L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        AuthCredential credential = AuthCredential.createLocal(1L, "test@example.com", "encodedPassword");
        given(authCredentialRepository.findByMemberId(1L)).willReturn(Optional.of(credential));

        MemberResponse response = memberService.getMemberById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("홍길동");
        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.phoneNumber()).isEqualTo("010-1234-5678");
    }

    @Test
    @DisplayName("admin이 다른 회원을 조회해도 회원 정보를 반환한다")
    void getMemberById_admin_success() {
        given(currentMemberProvider.hasRole(Role.ADMIN)).willReturn(true);

        Member member = Member.create("홍길동", "010-1234-5678");
        ReflectionTestUtils.setField(member, "id", 1L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        AuthCredential credential = AuthCredential.createLocal(1L, "test@example.com", "encodedPassword");
        given(authCredentialRepository.findByMemberId(1L)).willReturn(Optional.of(credential));

        MemberResponse response = memberService.getMemberById(1L);

        assertThat(response.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("본인도 admin도 아니면 AccessDeniedException을 던지고 조회하지 않는다")
    void getMemberById_notOwnerNotAdmin_throwsException() {
        given(currentMemberProvider.hasRole(Role.ADMIN)).willReturn(false);
        given(currentMemberProvider.getId()).willReturn(2L);

        assertThatThrownBy(() -> memberService.getMemberById(1L))
                .isInstanceOf(AccessDeniedException.class);

        verify(memberRepository, never()).findById(any());
    }

    @Test
    @DisplayName("존재하지 않는 회원이면 EntityNotFoundException을 던진다")
    void getMemberById_memberNotFound_throwsException() {
        given(currentMemberProvider.hasRole(Role.ADMIN)).willReturn(false);
        given(currentMemberProvider.getId()).willReturn(1L);
        given(memberRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.getMemberById(1L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("회원은 있는데 연동된 인증 정보가 없으면 EntityNotFoundException을 던진다")
    void getMemberById_credentialNotFound_throwsException() {
        given(currentMemberProvider.hasRole(Role.ADMIN)).willReturn(false);
        given(currentMemberProvider.getId()).willReturn(1L);

        Member member = Member.create("홍길동", "010-1234-5678");
        ReflectionTestUtils.setField(member, "id", 1L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(authCredentialRepository.findByMemberId(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.getMemberById(1L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("본인 정보를 수정하면 변경된 값을 반환하고 저장한다")
    void updateMember_self_success() {
        given(currentMemberProvider.getId()).willReturn(1L);

        Member member = Member.create("홍길동", "010-1234-5678");
        ReflectionTestUtils.setField(member, "id", 1L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        MemberUpdateRequest request = new MemberUpdateRequest("김철수", "010-9999-8888");

        MemberUpdateResponse response = memberService.updateMember(1L, request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("김철수");
        assertThat(response.phoneNumber()).isEqualTo("010-9999-8888");
        assertThat(member.getName()).isEqualTo("김철수");
        assertThat(member.getPhoneNumber()).isEqualTo("010-9999-8888");
    }

    @Test
    @DisplayName("name만 보내면 phoneNumber는 기존 값을 유지한 채 반환한다")
    void updateMember_partialNameOnly_keepsExistingPhoneNumber() {
        given(currentMemberProvider.getId()).willReturn(1L);

        Member member = Member.create("홍길동", "010-1234-5678");
        ReflectionTestUtils.setField(member, "id", 1L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        MemberUpdateRequest request = new MemberUpdateRequest("김철수", null);

        MemberUpdateResponse response = memberService.updateMember(1L, request);

        assertThat(response.name()).isEqualTo("김철수");
        assertThat(response.phoneNumber()).isEqualTo("010-1234-5678");
    }

    @Test
    @DisplayName("본인이 아니면 AccessDeniedException을 던지고 수정하지 않는다")
    void updateMember_notOwner_throwsException() {
        given(currentMemberProvider.getId()).willReturn(2L);

        MemberUpdateRequest request = new MemberUpdateRequest("김철수", "010-9999-8888");

        assertThatThrownBy(() -> memberService.updateMember(1L, request))
                .isInstanceOf(AccessDeniedException.class);

        verify(memberRepository, never()).findById(any());
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 회원이면 EntityNotFoundException을 던진다")
    void updateMember_memberNotFound_throwsException() {
        given(currentMemberProvider.getId()).willReturn(1L);
        given(memberRepository.findById(1L)).willReturn(Optional.empty());

        MemberUpdateRequest request = new MemberUpdateRequest("김철수", "010-9999-8888");

        assertThatThrownBy(() -> memberService.updateMember(1L, request))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("현재 비밀번호가 일치하면 새 비밀번호를 인코딩해서 저장한다")
    void changePassword_self_success() {
        given(currentMemberProvider.getId()).willReturn(1L);

        AuthCredential credential = AuthCredential.createLocal(1L, "test@example.com", "encodedOldPassword");
        given(authCredentialRepository.findByMemberId(1L)).willReturn(Optional.of(credential));

        given(passwordEncoder.matches("oldPassword123", "encodedOldPassword")).willReturn(true);
        given(passwordEncoder.encode("newPassword456")).willReturn("encodedNewPassword");

        PasswordChangeRequest request = new PasswordChangeRequest("oldPassword123", "newPassword456");

        memberService.changePassword(1L, request);

        assertThat(credential.getPasswordHash()).isEqualTo("encodedNewPassword");
    }

    @Test
    @DisplayName("본인이 아니면 AccessDeniedException을 던지고 인증정보를 조회하지 않는다")
    void changePassword_notOwner_throwsException() {
        given(currentMemberProvider.getId()).willReturn(2L);

        PasswordChangeRequest request = new PasswordChangeRequest("oldPassword123", "newPassword456");

        assertThatThrownBy(() -> memberService.changePassword(1L, request))
                .isInstanceOf(AccessDeniedException.class);

        verify(authCredentialRepository, never()).findByMemberId(any());
    }

    @Test
    @DisplayName("연동된 인증 정보가 없으면 EntityNotFoundException을 던진다")
    void changePassword_credentialNotFound_throwsException() {
        given(currentMemberProvider.getId()).willReturn(1L);
        given(authCredentialRepository.findByMemberId(1L)).willReturn(Optional.empty());

        PasswordChangeRequest request = new PasswordChangeRequest("oldPassword123", "newPassword456");

        assertThatThrownBy(() -> memberService.changePassword(1L, request))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("GOOGLE 전용 계정이면 AccessDeniedException을 던지고 비밀번호를 확인하지 않는다")
    void changePassword_googleAccount_throwsException() {
        given(currentMemberProvider.getId()).willReturn(1L);

        AuthCredential credential = AuthCredential.createGoogle(1L, AuthProvider.GOOGLE, "google-sub", "test@example.com");
        given(authCredentialRepository.findByMemberId(1L)).willReturn(Optional.of(credential));

        PasswordChangeRequest request = new PasswordChangeRequest("oldPassword123", "newPassword456");

        assertThatThrownBy(() -> memberService.changePassword(1L, request))
                .isInstanceOf(AccessDeniedException.class);

        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    @DisplayName("현재 비밀번호가 일치하지 않으면 AuthenticationFailedException을 던지고 저장하지 않는다")
    void changePassword_wrongCurrentPassword_throwsException() {
        given(currentMemberProvider.getId()).willReturn(1L);

        AuthCredential credential = AuthCredential.createLocal(1L, "test@example.com", "encodedOldPassword");
        given(authCredentialRepository.findByMemberId(1L)).willReturn(Optional.of(credential));

        given(passwordEncoder.matches("wrongPassword", "encodedOldPassword")).willReturn(false);

        PasswordChangeRequest request = new PasswordChangeRequest("wrongPassword", "newPassword456");

        assertThatThrownBy(() -> memberService.changePassword(1L, request))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(passwordEncoder, never()).encode(any());
        assertThat(credential.getPasswordHash()).isEqualTo("encodedOldPassword");
    }

    @Test
    @DisplayName("본인이 탈퇴하면 회원/인증정보를 익명화하고 refreshToken을 삭제한다")
    void withdrawMember_self_success() {
        given(currentMemberProvider.getId()).willReturn(1L);

        Member member = Member.create("홍길동", "010-1234-5678");
        ReflectionTestUtils.setField(member, "id", 1L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        AuthCredential credential = AuthCredential.createLocal(1L, "test@example.com", "encodedPassword");
        given(authCredentialRepository.findByMemberId(1L)).willReturn(Optional.of(credential));

        memberService.withdrawMember(1L);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(member.getName()).isEqualTo("탈퇴한 회원");
        assertThat(member.getPhoneNumber()).isNull();
        assertThat(member.getDeletedAt()).isNotNull();
        assertThat(credential.getEmail()).isEqualTo("withdrawn-1@deleted.local");
        assertThat(credential.getPasswordHash()).isNull();
        verify(refreshTokenRepository).deleteByMemberId(1L);
        verify(accessTokenRepository).blacklistByMemberId(1L);
    }

    @Test
    @DisplayName("본인이 아니면 AccessDeniedException을 던지고 탈퇴 처리하지 않는다")
    void withdrawMember_notOwner_throwsException() {
        given(currentMemberProvider.getId()).willReturn(2L);

        assertThatThrownBy(() -> memberService.withdrawMember(1L))
                .isInstanceOf(AccessDeniedException.class);

        verify(memberRepository, never()).findById(any());
        verify(refreshTokenRepository, never()).deleteByMemberId(any());
        verify(accessTokenRepository, never()).blacklistByMemberId(any());
    }

    @Test
    @DisplayName("존재하지 않는 회원이면 EntityNotFoundException을 던진다")
    void withdrawMember_memberNotFound_throwsException() {
        given(currentMemberProvider.getId()).willReturn(1L);
        given(memberRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.withdrawMember(1L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("이미 탈퇴한 회원이면 IllegalStateException을 던지고 인증정보는 조회하지 않는다")
    void withdrawMember_alreadyWithdrawn_throwsException() {
        given(currentMemberProvider.getId()).willReturn(1L);

        Member member = Member.create("홍길동", "010-1234-5678");
        ReflectionTestUtils.setField(member, "id", 1L);
        member.withdraw();
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> memberService.withdrawMember(1L))
                .isInstanceOf(IllegalStateException.class);

        verify(authCredentialRepository, never()).findByMemberId(any());
        verify(refreshTokenRepository, never()).deleteByMemberId(any());
        verify(accessTokenRepository, never()).blacklistByMemberId(any());
    }

    @Test
    @DisplayName("회원은 있는데 연동된 인증 정보가 없으면 EntityNotFoundException을 던진다")
    void withdrawMember_credentialNotFound_throwsException() {
        given(currentMemberProvider.getId()).willReturn(1L);

        Member member = Member.create("홍길동", "010-1234-5678");
        ReflectionTestUtils.setField(member, "id", 1L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(authCredentialRepository.findByMemberId(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.withdrawMember(1L))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
