package com.openbake.seller.application;

import com.openbake.common.exception.*;
import com.openbake.common.security.Authorities;
import com.openbake.common.security.CurrentMemberProvider;
import com.openbake.seller.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class SellerService {

    private final SellerRepository sellerRepository;
    private final BusinessRegistry mockBusinessRegistry;
    private final BankRegistry mockBankRegistry;
    private final CurrentMemberProvider currentMemberProvider;
    private final AccountVerificationRepository accountVerificationRepository;

    public BusinessVerificationResult verifyBusiness(BusinessVerificationCommand command) {
         verifyBusinessOrThrow(command.businessNumber(), command.businessRepresentativeName());
        return new BusinessVerificationResult(true, command.businessNumber(), LocalDateTime.now());
    }

    public AccountVerificationStartResult requestAccountVerification(AccountVerificationStartCommand command) {
        if (!mockBankRegistry.isValidBankCode(command.bankCode())) {
            throw new InvalidSettlementAccountException();
        }

        Long memberId = currentMemberProvider.getId();

        String verificationRequestId = "vr_" + UUID.randomUUID();
        String code = generateCode();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(3);

        AccountVerificationSession session = new AccountVerificationSession(
                memberId, command.bankCode(), command.accountNumber(), command.accountHolder(), code, expiresAt);
        accountVerificationRepository.saveSession(verificationRequestId, session);

        // [목업 안내] 실제 1원 송금 대신 서버 로그로만 코드를 남김. DEV 환경에선 getMockVerificationCode API로 조회.
        log.info("[MOCK] 계좌 인증 코드 발급 - verificationRequestId={}, code={}", verificationRequestId, code);

        return new AccountVerificationStartResult(verificationRequestId, expiresAt);
    }

    public AccountVerificationCodeResult getMockVerificationCode(String verificationRequestId) {
        AccountVerificationSession session = accountVerificationRepository.findSession(verificationRequestId)
                .orElseThrow(() -> new EntityNotFoundException("대상을 찾을 수 없습니다."));

        return new AccountVerificationCodeResult(verificationRequestId, session.code(), session.expiresAt());
    }

    public AccountVerificationConfirmResult verifyAccount(
            String verificationRequestId, AccountVerificationConfirmCommand command) {
        Long memberId = currentMemberProvider.getId();

        AccountVerificationSession session = accountVerificationRepository.findSession(verificationRequestId)
                .orElseThrow(() -> new EntityNotFoundException("대상을 찾을 수 없습니다."));

        if (!session.memberId().equals(memberId)) {
            throw new EntityNotFoundException("대상을 찾을 수 없습니다.");
        }

        if (session.expiresAt().isBefore(LocalDateTime.now())) {
            accountVerificationRepository.deleteSession(verificationRequestId);
            throw new AccountVerificationExpiredException();
        }

        if (!session.code().equals(command.verificationCode())) {
            throw new AccountVerificationFailedException();
        }

        LocalDateTime verifiedAt = LocalDateTime.now();
        VerifiedAccount verifiedAccount = new VerifiedAccount(
                session.bankCode(), session.accountNumber(), session.accountHolder(), verifiedAt);
        accountVerificationRepository.saveVerifiedAccount(memberId, verifiedAccount);
        accountVerificationRepository.deleteSession(verificationRequestId);

        return new AccountVerificationConfirmResult(true, verifiedAt);
    }

    public ApplicationCreateResult applySeller(ApplicationCreateCommand command) {
        Long memberId = currentMemberProvider.getId();

        if (sellerRepository.findByMemberId(memberId).isPresent()) {
            throw new DuplicateSellerApplicationException();
        }

        verifyBusinessOrThrow(command.businessNumber(), command.businessRepresentativeName());

        VerifiedAccount verifiedAccount = accountVerificationRepository.findVerifiedAccountByMemberId(memberId)
                .orElseThrow(AccountNotVerifiedException::new);

        Seller seller = new Seller(
                memberId, command.bakeryName(), command.businessNumber(), command.businessAddress(),
                command.businessRepresentativeName(), true, verifiedAccount.bankCode(),
                verifiedAccount.accountNumber(), verifiedAccount.accountHolder(), true
        );
        Seller saved = sellerRepository.save(seller);

        return new ApplicationCreateResult(saved.getId(), saved.getMemberId(), saved.getBakeryName(), saved.getApplicationStatus());
    }

    private void verifyBusinessOrThrow(String businessNumber, String businessRepresentativeName) {
        boolean verified = mockBusinessRegistry.isRegistered(businessNumber,  businessRepresentativeName);
        log.info("[MOCK] 사업자 인증 시도 - businessNumber={}, verified={}", businessNumber, verified);

        if (!verified) {
            throw new BusinessVerificationFailedException();
        }
    }

    public ApplicationStatusUpdateResult updateApplicationStatus(Long id, ApplicationStatusUpdateCommand command) {
        if (!currentMemberProvider.hasAuthority(Authorities.ROLE_ADMIN)) {
            throw new AdminAccessDeniedException();
        }

        Seller seller = sellerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("대상을 찾을 수 없습니다."));

        if (command.applicationStatus() == ApplicationStatus.APPROVED) {
            seller.approve();
        } else {
            seller.reject(command.rejectReason());
        }

        Seller saved = sellerRepository.save(seller);

        return new ApplicationStatusUpdateResult(saved.getId(), saved.getApplicationStatus(), saved.getRejectReason(), saved.getUpdatedAt());
    }

    public MySellerResult getMySeller() {
        Long memberId = currentMemberProvider.getId();
        Seller seller = sellerRepository.findByMemberId(memberId)
                .orElseThrow(() -> new EntityNotFoundException("대상을 찾을 수 없습니다."));

        return new MySellerResult(
                seller.getId(),
                seller.getMemberId(),
                seller.getBakeryName(),
                seller.getBusinessNumber(),
                seller.getApplicationStatus(),
                seller.getRejectReason(),
                seller.getSettlementBankCode(),
                maskAccountNumber(seller.getSettlementAccountNumber()),
                seller.isAccountVerified(),
                seller.getAccountVerifiedAt()
        );
    }

    public SellerResult getSeller(Long id) {
        Seller seller = sellerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("대상을 찾을 수 없습니다."));

        return new SellerResult(
                seller.getId(),
                seller.getMemberId(),
                seller.getBakeryName(),
                seller.getBusinessNumber(),
                seller.getApplicationStatus(),
                seller.getSettlementBankCode(),
                maskAccountNumber(seller.getSettlementAccountNumber()),
                seller.isAccountVerified(),
                seller.getAccountVerifiedAt()
        );
    }

    public List<MySellerResult> getPendingSellers(ApplicationStatus applicationStatus) {
        if (!currentMemberProvider.hasAuthority(Authorities.ROLE_ADMIN)) {
            throw new AdminAccessDeniedException();
        }

        return sellerRepository.findByApplicationStatus(applicationStatus).stream()
                .map(seller -> new MySellerResult(
                        seller.getId(),
                        seller.getMemberId(),
                        seller.getBakeryName(),
                        seller.getBusinessNumber(),
                        seller.getApplicationStatus(),
                        seller.getRejectReason(),
                        seller.getSettlementBankCode(),
                        maskAccountNumber(seller.getSettlementAccountNumber()),
                        seller.isAccountVerified(),
                        seller.getAccountVerifiedAt()
                ))
                .toList();
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber.length() <= 7) {
            return accountNumber;
        }
        String prefix = accountNumber.substring(0, 3);
        String suffix = accountNumber.substring(accountNumber.length() - 4);
        String masked = "*".repeat(accountNumber.length() - 7);
        return prefix + "-" + masked + "-" + suffix;
    }

    private String generateCode() {
        return String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
