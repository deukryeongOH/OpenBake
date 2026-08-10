package com.openbake.payment.infrastructure;

import com.openbake.payment.domain.AccountType;
import com.openbake.payment.domain.DepositAccount;
import com.openbake.payment.domain.DepositAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DepositAccountRepositoryAdapter implements DepositAccountRepository {

    private final DepositAccountJpaRepository jpaRepository;

    @Override
    public DepositAccount save(DepositAccount depositAccount) {
        return jpaRepository.save(depositAccount);
    }

    @Override
    public Optional<DepositAccount> findByMemberId(Long memberId) {
        return jpaRepository.findByMemberId(memberId);
    }

    @Override
    public Optional<DepositAccount> findByMemberIdForUpdate(Long memberId) {
        return jpaRepository.findByMemberIdForUpdate(memberId);
    }

    @Override
    public boolean existsByAccountType(AccountType accountType) {
        return jpaRepository.existsByAccountType(accountType);
    }

    @Override
    public Optional<DepositAccount> findByAccountType(AccountType accountType) {
        return jpaRepository.findByAccountType(accountType);
    }
}
