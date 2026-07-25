package com.openbake.seller.application;

import com.openbake.common.exception.AccountNotVerifiedException;
import com.openbake.common.exception.AccountVerificationExpiredException;
import com.openbake.common.exception.AccountVerificationFailedException;
import com.openbake.common.exception.AdminAccessDeniedException;
import com.openbake.common.exception.BusinessVerificationFailedException;
import com.openbake.common.exception.DuplicateSellerApplicationException;
import com.openbake.common.exception.EntityNotFoundException;
import com.openbake.common.exception.InvalidApplicationStatusException;
import com.openbake.common.exception.InvalidSettlementAccountException;
import com.openbake.common.security.CurrentMemberProvider;
import com.openbake.member.domain.Role;
import com.openbake.seller.domain.AccountVerificationRepository;
import com.openbake.seller.domain.AccountVerificationSession;
import com.openbake.seller.domain.ApplicationStatus;
import com.openbake.seller.domain.Seller;
import com.openbake.seller.domain.SellerRepository;
import com.openbake.seller.domain.VerifiedAccount;
import com.openbake.seller.infrastructure.MockBankRegistry;
import com.openbake.seller.infrastructure.MockBusinessRegistry;
import com.openbake.seller.presentation.dto.AccountVerificationCodeResponse;
import com.openbake.seller.presentation.dto.AccountVerificationConfirmRequest;
import com.openbake.seller.presentation.dto.AccountVerificationConfirmResponse;
import com.openbake.seller.presentation.dto.AccountVerificationStartRequest;
import com.openbake.seller.presentation.dto.AccountVerificationStartResponse;
import com.openbake.seller.presentation.dto.ApplicationStatusUpdateRequest;
import com.openbake.seller.presentation.dto.ApplicationStatusUpdateResponse;
import com.openbake.seller.presentation.dto.ApplicationCreateRequest;
import com.openbake.seller.presentation.dto.ApplicationCreateResponse;
import com.openbake.seller.presentation.dto.BusinessVerificationRequest;
import com.openbake.seller.presentation.dto.BusinessVerificationResponse;
import com.openbake.seller.presentation.dto.SellerResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SellerServiceTest {

    @Mock
    private SellerRepository sellerRepository;

    @Mock
    private MockBusinessRegistry mockBusinessRegistry;

    @Mock
    private MockBankRegistry mockBankRegistry;

    @Mock
    private CurrentMemberProvider currentMemberProvider;

    @Mock
    private AccountVerificationRepository accountVerificationRepository;

    @InjectMocks
    private SellerService sellerService;

    @Test
    @DisplayName("등록된 사업자 정보와 일치하면 인증에 성공한다")
    void verifyBusiness_success() {
        BusinessVerificationRequest request =
                new BusinessVerificationRequest("123-45-67890", "서울시", "이세종");
        given(mockBusinessRegistry.isRegistered("123-45-67890", "이세종")).willReturn(true);

        BusinessVerificationResponse response = sellerService.verifyBusiness(request);

        assertThat(response.verified()).isTrue();
        assertThat(response.businessNumber()).isEqualTo("123-45-67890");
    }

    @Test
    @DisplayName("등록된 사업자 정보와 불일치하면 예외가 발생한다")
    void verifyBusiness_fail() {
        BusinessVerificationRequest request =
                new BusinessVerificationRequest("123-45-67890", "서울시", "홍길동");
        given(mockBusinessRegistry.isRegistered("123-45-67890", "홍길동")).willReturn(false);

        assertThatThrownBy(() -> sellerService.verifyBusiness(request))
                .isInstanceOf(BusinessVerificationFailedException.class);
    }

    @Test
    @DisplayName("등록되지 않은 은행 코드면 예외가 발생한다")
    void requestAccountVerification_invalidBankCode() {
        AccountVerificationStartRequest request =
                new AccountVerificationStartRequest("999", "1101234567", "이세종");
        given(mockBankRegistry.isValidBankCode("999")).willReturn(false);

        assertThatThrownBy(() -> sellerService.requestAccountVerification(request))
                .isInstanceOf(InvalidSettlementAccountException.class);
    }

    @Test
    @DisplayName("계좌 인증 요청 성공 시 세션을 저장하고 만료 시각을 반환한다")
    void requestAccountVerification_success() {
        AccountVerificationStartRequest request =
                new AccountVerificationStartRequest("088", "1101234567", "이세종");
        given(mockBankRegistry.isValidBankCode("088")).willReturn(true);
        given(currentMemberProvider.getId()).willReturn(1L);

        AccountVerificationStartResponse response = sellerService.requestAccountVerification(request);

        assertThat(response.verificationRequestId()).isNotBlank();
        assertThat(response.expiresAt()).isAfter(LocalDateTime.now());
        verify(accountVerificationRepository)
                .saveSession(eq(response.verificationRequestId()), any(AccountVerificationSession.class));
    }

    @Test
    @DisplayName("세션이 존재하면 목업 인증 코드를 반환한다")
    void getMockVerificationCode_success() {
        AccountVerificationSession session = new AccountVerificationSession(
                1L, "088", "1101234567", "이세종", "1234", LocalDateTime.now().plusMinutes(3));
        given(accountVerificationRepository.findSession("vr_1")).willReturn(Optional.of(session));

        AccountVerificationCodeResponse response = sellerService.getMockVerificationCode("vr_1");

        assertThat(response.code()).isEqualTo("1234");
    }

    @Test
    @DisplayName("세션이 없으면 목업 인증 코드 조회 시 예외가 발생한다")
    void getMockVerificationCode_notFound() {
        given(accountVerificationRepository.findSession("vr_1")).willReturn(Optional.empty());

        assertThatThrownBy(() -> sellerService.getMockVerificationCode("vr_1"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("세션이 없으면 계좌 인증 확인 시 예외가 발생한다")
    void verifyAccount_sessionNotFound() {
        given(currentMemberProvider.getId()).willReturn(1L);
        given(accountVerificationRepository.findSession("vr_1")).willReturn(Optional.empty());

        AccountVerificationConfirmRequest request = new AccountVerificationConfirmRequest("1234");

        assertThatThrownBy(() -> sellerService.verifyAccount("vr_1", request))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("다른 회원이 만든 인증 요청이면 예외가 발생한다")
    void verifyAccount_otherMember() {
        given(currentMemberProvider.getId()).willReturn(2L);
        AccountVerificationSession session = new AccountVerificationSession(
                1L, "088", "1101234567", "이세종", "1234", LocalDateTime.now().plusMinutes(3));
        given(accountVerificationRepository.findSession("vr_1")).willReturn(Optional.of(session));

        AccountVerificationConfirmRequest request = new AccountVerificationConfirmRequest("1234");

        assertThatThrownBy(() -> sellerService.verifyAccount("vr_1", request))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("인증 유효 시간이 지나면 예외가 발생하고 세션이 삭제된다")
    void verifyAccount_expired() {
        given(currentMemberProvider.getId()).willReturn(1L);
        AccountVerificationSession session = new AccountVerificationSession(
                1L, "088", "1101234567", "이세종", "1234", LocalDateTime.now().minusMinutes(1));
        given(accountVerificationRepository.findSession("vr_1")).willReturn(Optional.of(session));

        AccountVerificationConfirmRequest request = new AccountVerificationConfirmRequest("1234");

        assertThatThrownBy(() -> sellerService.verifyAccount("vr_1", request))
                .isInstanceOf(AccountVerificationExpiredException.class);
        verify(accountVerificationRepository).deleteSession("vr_1");
    }

    @Test
    @DisplayName("인증 코드가 일치하지 않으면 예외가 발생한다")
    void verifyAccount_codeMismatch() {
        given(currentMemberProvider.getId()).willReturn(1L);
        AccountVerificationSession session = new AccountVerificationSession(
                1L, "088", "1101234567", "이세종", "1234", LocalDateTime.now().plusMinutes(3));
        given(accountVerificationRepository.findSession("vr_1")).willReturn(Optional.of(session));

        AccountVerificationConfirmRequest request = new AccountVerificationConfirmRequest("9999");

        assertThatThrownBy(() -> sellerService.verifyAccount("vr_1", request))
                .isInstanceOf(AccountVerificationFailedException.class);
    }

    @Test
    @DisplayName("인증 성공 시 계좌 정보를 memberId 기준으로 저장하고 세션을 삭제한다")
    void verifyAccount_success() {
        given(currentMemberProvider.getId()).willReturn(1L);
        AccountVerificationSession session = new AccountVerificationSession(
                1L, "088", "1101234567", "이세종", "1234", LocalDateTime.now().plusMinutes(3));
        given(accountVerificationRepository.findSession("vr_1")).willReturn(Optional.of(session));

        AccountVerificationConfirmRequest request = new AccountVerificationConfirmRequest("1234");

        AccountVerificationConfirmResponse response = sellerService.verifyAccount("vr_1", request);

        assertThat(response.verified()).isTrue();
        verify(accountVerificationRepository).saveVerifiedAccount(eq(1L), any(VerifiedAccount.class));
        verify(accountVerificationRepository).deleteSession("vr_1");
    }

    @Test
    @DisplayName("사업자/계좌 인증을 모두 마친 회원은 판매자 신청에 성공한다")
    void applySeller_success() {
        ApplicationCreateRequest request = new ApplicationCreateRequest("세종베이커리", "123-45-67890", "서울시", "이세종");
        given(currentMemberProvider.getId()).willReturn(1L);
        given(sellerRepository.findByMemberId(1L)).willReturn(Optional.empty());
        given(mockBusinessRegistry.isRegistered("123-45-67890", "이세종")).willReturn(true);
        given(accountVerificationRepository.findVerifiedAccountByMemberId(1L))
                .willReturn(Optional.of(new VerifiedAccount("088", "1101234567", "이세종", LocalDateTime.now())));
        given(sellerRepository.save(any(Seller.class))).willAnswer(invocation -> invocation.getArgument(0));

        ApplicationCreateResponse response = sellerService.applySeller(request);

        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.bakeryName()).isEqualTo("세종베이커리");
        assertThat(response.applicationStatus()).isEqualTo(ApplicationStatus.PENDING);
    }

    @Test
    @DisplayName("이미 판매자 신청을 완료한 회원이 재신청하면 예외가 발생한다")
    void applySeller_duplicate() {
        ApplicationCreateRequest request = new ApplicationCreateRequest("세종베이커리", "123-45-67890", "서울시", "이세종");
        given(currentMemberProvider.getId()).willReturn(1L);
        given(sellerRepository.findByMemberId(1L)).willReturn(Optional.of(
                new Seller(1L, "세종베이커리", "123-45-67890", "서울시", "이세종",
                        true, "088", "1101234567", "이세종", true)));

        assertThatThrownBy(() -> sellerService.applySeller(request))
                .isInstanceOf(DuplicateSellerApplicationException.class);
    }

    @Test
    @DisplayName("요청에 담긴 사업자 정보가 등록된 정보와 불일치하면 예외가 발생한다")
    void applySeller_businessMismatch() {
        ApplicationCreateRequest request = new ApplicationCreateRequest("세종베이커리", "123-45-67890", "서울시", "홍길동");
        given(currentMemberProvider.getId()).willReturn(1L);
        given(sellerRepository.findByMemberId(1L)).willReturn(Optional.empty());
        given(mockBusinessRegistry.isRegistered("123-45-67890", "홍길동")).willReturn(false);

        assertThatThrownBy(() -> sellerService.applySeller(request))
                .isInstanceOf(BusinessVerificationFailedException.class);
    }

    @Test
    @DisplayName("계좌 인증이 완료되지 않은 상태로 신청하면 예외가 발생한다")
    void applySeller_accountNotVerified() {
        ApplicationCreateRequest request = new ApplicationCreateRequest("세종베이커리", "123-45-67890", "서울시", "이세종");
        given(currentMemberProvider.getId()).willReturn(1L);
        given(sellerRepository.findByMemberId(1L)).willReturn(Optional.empty());
        given(mockBusinessRegistry.isRegistered("123-45-67890", "이세종")).willReturn(true);
        given(accountVerificationRepository.findVerifiedAccountByMemberId(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sellerService.applySeller(request))
                .isInstanceOf(AccountNotVerifiedException.class);
    }

    @Test
    @DisplayName("admin 권한이 없으면 승인/반려 처리 시 예외가 발생한다")
    void updateApplicationStatus_notAdmin() {
        given(currentMemberProvider.hasRole(Role.ADMIN)).willReturn(false);
        ApplicationStatusUpdateRequest request = new ApplicationStatusUpdateRequest(ApplicationStatus.APPROVED, null);

        assertThatThrownBy(() -> sellerService.updateApplicationStatus(1L, request))
                .isInstanceOf(AdminAccessDeniedException.class);
    }

    @Test
    @DisplayName("존재하지 않는 판매자 ID면 예외가 발생한다")
    void updateApplicationStatus_notFound() {
        given(currentMemberProvider.hasRole(Role.ADMIN)).willReturn(true);
        given(sellerRepository.findById(1L)).willReturn(Optional.empty());
        ApplicationStatusUpdateRequest request = new ApplicationStatusUpdateRequest(ApplicationStatus.APPROVED, null);

        assertThatThrownBy(() -> sellerService.updateApplicationStatus(1L, request))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("pending 상태의 판매자를 승인하면 상태가 approved로 바뀐다")
    void updateApplicationStatus_approve_success() {
        Seller seller = new Seller(1L, "세종베이커리", "123-45-67890", "서울시", "이세종",
                true, "088", "1101234567", "이세종", true);
        given(currentMemberProvider.hasRole(Role.ADMIN)).willReturn(true);
        given(sellerRepository.findById(1L)).willReturn(Optional.of(seller));
        given(sellerRepository.save(seller)).willReturn(seller);
        ApplicationStatusUpdateRequest request = new ApplicationStatusUpdateRequest(ApplicationStatus.APPROVED, null);

        ApplicationStatusUpdateResponse response = sellerService.updateApplicationStatus(1L, request);

        assertThat(response.applicationStatus()).isEqualTo(ApplicationStatus.APPROVED);
    }

    @Test
    @DisplayName("pending 상태의 판매자를 반려하면 상태가 rejected로 바뀌고 사유가 저장된다")
    void updateApplicationStatus_reject_success() {
        Seller seller = new Seller(1L, "세종베이커리", "123-45-67890", "서울시", "이세종",
                true, "088", "1101234567", "이세종", true);
        given(currentMemberProvider.hasRole(Role.ADMIN)).willReturn(true);
        given(sellerRepository.findById(1L)).willReturn(Optional.of(seller));
        given(sellerRepository.save(seller)).willReturn(seller);
        ApplicationStatusUpdateRequest request =
                new ApplicationStatusUpdateRequest(ApplicationStatus.REJECTED, "주소 불일치");

        ApplicationStatusUpdateResponse response = sellerService.updateApplicationStatus(1L, request);

        assertThat(response.applicationStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(response.rejectReason()).isEqualTo("주소 불일치");
    }

    @Test
    @DisplayName("pending이 아닌 판매자를 재처리하면 예외가 발생한다")
    void updateApplicationStatus_notPending() {
        Seller seller = new Seller(1L, "세종베이커리", "123-45-67890", "서울시", "이세종",
                true, "088", "1101234567", "이세종", true);
        seller.approve();
        given(currentMemberProvider.hasRole(Role.ADMIN)).willReturn(true);
        given(sellerRepository.findById(1L)).willReturn(Optional.of(seller));
        ApplicationStatusUpdateRequest request = new ApplicationStatusUpdateRequest(ApplicationStatus.APPROVED, null);

        assertThatThrownBy(() -> sellerService.updateApplicationStatus(1L, request))
                .isInstanceOf(InvalidApplicationStatusException.class);
    }

    @Test
    @DisplayName("판매자 조회 성공 시 계좌번호가 마스킹되어 반환된다")
    void getSeller_success() {
        Seller seller = new Seller(1L, "세종베이커리", "123-45-67890", "서울시", "이세종",
                true, "088", "1101234567", "이세종", true);
        given(sellerRepository.findById(1L)).willReturn(Optional.of(seller));

        SellerResponse response = sellerService.getSeller(1L);

        assertThat(response.bakeryName()).isEqualTo("세종베이커리");
        assertThat(response.settlementAccountNumberMasked()).isEqualTo("110-***-4567");
        assertThat(response.accountVerified()).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 판매자 ID로 조회하면 예외가 발생한다")
    void getSeller_notFound() {
        given(sellerRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sellerService.getSeller(1L))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
