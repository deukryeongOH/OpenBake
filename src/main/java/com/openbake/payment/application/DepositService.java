package com.openbake.payment.application;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.payment.domain.ChargeStatus;
import com.openbake.payment.domain.DepositAccount;
import com.openbake.payment.domain.TransactionType;
import com.openbake.payment.domain.WalletTransaction;
import com.openbake.payment.infrastructure.ChargeRequestRepository;
import com.openbake.payment.infrastructure.DepositAccountRepository;
import com.openbake.payment.infrastructure.WalletTransactionRepository;
import com.openbake.payment.presentation.dto.DepositResponse;
import com.openbake.payment.presentation.dto.TransactionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DepositService {

    private static final int MAX_PAGE_SIZE = 50;

    private final DepositAccountRepository depositAccountRepository;
    private final ChargeRequestRepository chargeRequestRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    /**
     * 예치금 잔액 조회.
     * 계좌가 없으면 자동 생성(잔액 0원)해서 반환한다.
     * → 아직 충전한 적 없는 신규 회원도 조회 가능.
     */
    @Transactional
    public DepositResponse getBalance(Long memberId) {
        DepositAccount account = depositAccountRepository.findByMemberId(memberId)
                .orElseGet(() -> depositAccountRepository.save(
                        DepositAccount.createMemberAccount(memberId)
                ));

        boolean hasChargeInProgress = chargeRequestRepository.existsByMemberIdAndStatusIn(
                memberId, List.of(ChargeStatus.READY, ChargeStatus.IN_PROGRESS)
        );

        return new DepositResponse(memberId, account.getBalance(), hasChargeInProgress);
    }

    /**
     * 거래 내역 조회 (5-2).
     * 회원의 WalletTransaction을 페이징 조회한다.
     * transactionType이 지정되면 해당 유형만 필터링.
     */
    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(Long memberId, TransactionType transactionType,
                                                     int page, int size) {
        int clampedSize = Math.min(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(page, clampedSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        DepositAccount account = depositAccountRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPOSIT_ACCOUNT_NOT_FOUND));

        Page<WalletTransaction> transactions;
        if (transactionType != null) {
            transactions = walletTransactionRepository.findByDepositAccountIdAndTransactionType(
                    account.getId(), transactionType, pageable);
        } else {
            transactions = walletTransactionRepository.findByDepositAccountId(account.getId(), pageable);
        }

        return transactions.map(this::toResponse);
    }

    private TransactionResponse toResponse(WalletTransaction tx) {
        String description = generateDescription(tx);
        return new TransactionResponse(
                tx.getId(),
                tx.getTransactionType().name(),
                tx.getAmount(),
                tx.getBalanceAfter(),
                description,
                tx.getReferenceType().name(),
                tx.getReferenceId(),
                tx.getCreatedAt()
        );
    }

    private String generateDescription(WalletTransaction tx) {
        return switch (tx.getTransactionType()) {
            case CHARGE -> "예치금 충전";
            case PAYMENT -> "주문 결제";
            case REFUND -> "주문 환불";
        };
    }
}
