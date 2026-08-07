package com.openbake.payment.infrastructure;

import com.openbake.payment.domain.TransactionType;
import com.openbake.payment.domain.WalletTransaction;
import com.openbake.payment.domain.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class WalletTransactionRepositoryAdapter implements WalletTransactionRepository {

    private final WalletTransactionJpaRepository jpaRepository;

    @Override
    public WalletTransaction save(WalletTransaction walletTransaction) {
        return jpaRepository.save(walletTransaction);
    }

    @Override
    public Page<WalletTransaction> findByDepositAccountId(Long depositAccountId, Pageable pageable) {
        return jpaRepository.findByDepositAccountId(depositAccountId, pageable);
    }

    @Override
    public Page<WalletTransaction> findByDepositAccountIdAndTransactionType(
            Long depositAccountId, TransactionType transactionType, Pageable pageable) {
        return jpaRepository.findByDepositAccountIdAndTransactionType(depositAccountId, transactionType, pageable);
    }
}
