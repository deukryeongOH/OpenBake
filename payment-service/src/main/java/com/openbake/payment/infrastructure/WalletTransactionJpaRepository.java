package com.openbake.payment.infrastructure;

import com.openbake.payment.domain.TransactionType;
import com.openbake.payment.domain.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletTransactionJpaRepository extends JpaRepository<WalletTransaction, Long> {

    Page<WalletTransaction> findByDepositAccountId(Long depositAccountId, Pageable pageable);

    Page<WalletTransaction> findByDepositAccountIdAndTransactionType(
            Long depositAccountId, TransactionType transactionType, Pageable pageable);
}
