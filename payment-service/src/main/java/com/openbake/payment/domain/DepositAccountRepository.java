package com.openbake.payment.domain;

import java.util.Optional;

public interface DepositAccountRepository {
    DepositAccount save(DepositAccount depositAccount);
    Optional<DepositAccount> findByMemberId(Long memberId);
    Optional<DepositAccount> findByMemberIdForUpdate(Long memberId);
    boolean existsByAccountType(AccountType accountType);
    Optional<DepositAccount> findByAccountType(AccountType accountType);
}
