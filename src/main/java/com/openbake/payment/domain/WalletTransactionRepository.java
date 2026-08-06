package com.openbake.payment.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WalletTransactionRepository {
    WalletTransaction save(WalletTransaction walletTransaction);
    Page<WalletTransaction> findByDepositAccountId(Long depositAccountId, Pageable pageable);
    Page<WalletTransaction> findByDepositAccountIdAndTransactionType(
            Long depositAccountId, TransactionType transactionType, Pageable pageable);
}
