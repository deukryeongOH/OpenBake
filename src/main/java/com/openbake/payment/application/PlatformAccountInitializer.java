package com.openbake.payment.application;

import com.openbake.payment.domain.AccountType;
import com.openbake.payment.domain.DepositAccount;
import com.openbake.payment.domain.DepositAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 서버 시작 시 PLATFORM 계좌가 없으면 자동 생성.
 * deposit_accounts에 PLATFORM row가 1개 있어야 PaymentService.pay()가 동작한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformAccountInitializer implements ApplicationRunner {

    private final DepositAccountRepository depositAccountRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (!depositAccountRepository.existsByAccountType(AccountType.PLATFORM)) {
            depositAccountRepository.save(DepositAccount.createPlatformAccount());
            log.info("[초기화] PLATFORM 계좌 생성 완료");
        }
    }
}
