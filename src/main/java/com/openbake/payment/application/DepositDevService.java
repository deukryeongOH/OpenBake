package com.openbake.payment.application;

import com.openbake.payment.domain.DepositAccount;
import com.openbake.payment.domain.ReferenceType;
import com.openbake.payment.domain.TransactionType;
import com.openbake.payment.domain.WalletTransaction;
import com.openbake.payment.domain.DepositAccountRepository;
import com.openbake.payment.domain.WalletTransactionRepository;
import com.openbake.payment.application.dto.DepositResult;
import com.openbake.payment.application.dto.DevChargeCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

// [DEV 전용] PG 없이 예치금 직접 충전 서비스. 운영(prod) 프로파일에서는 빈이 등록되지 않음.
@Profile({"local", "dev"})
@Service
@RequiredArgsConstructor
public class DepositDevService {

    private final DepositAccountRepository depositAccountRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    @Transactional
    public DepositResult devCharge(DevChargeCommand command) {
        Long memberId = command.memberId();
        BigDecimal amount = command.amount();

        DepositAccount account = depositAccountRepository.findByMemberIdForUpdate(memberId)
                .orElseGet(() -> depositAccountRepository.save(
                        DepositAccount.createMemberAccount(memberId)
                ));

        account.charge(amount);

        walletTransactionRepository.save(WalletTransaction.create(
                account,
                TransactionType.CHARGE,
                amount,
                account.getBalance(),
                ReferenceType.CHARGE_REQUEST,
                0L  // dev 충전은 ChargeRequest가 없으므로 0
        ));

        return new DepositResult(memberId, account.getBalance(), false);
    }
}
