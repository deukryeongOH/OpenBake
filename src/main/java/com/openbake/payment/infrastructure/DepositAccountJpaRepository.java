package com.openbake.payment.infrastructure;

import com.openbake.payment.domain.AccountType;
import com.openbake.payment.domain.DepositAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DepositAccountJpaRepository extends JpaRepository<DepositAccount, Long> {

    Optional<DepositAccount> findByMemberId(Long memberId);

    boolean existsByAccountType(AccountType accountType);

    Optional<DepositAccount> findByAccountType(AccountType accountType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DepositAccount d WHERE d.memberId = :memberId")
    Optional<DepositAccount> findByMemberIdForUpdate(@Param("memberId") Long memberId);
}
