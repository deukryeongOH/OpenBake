package com.openbake.payment.application;

import com.openbake.payment.domain.DepositAccount;
import com.openbake.payment.infrastructure.DepositAccountJpaRepository;
import com.openbake.payment.presentation.dto.DepositResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DepositServiceTest {

    @Autowired
    private DepositService depositService;

    @Autowired
    private DepositAccountJpaRepository depositAccountJpaRepository;

    @Test
    @DisplayName("기존 계좌가 있으면 잔액을 반환한다")
    void returnsBalanceForExistingAccount() {
        // given — 잔액 30,000원인 계좌 생성
        DepositAccount account = DepositAccount.createMemberAccount(1L);
        account.charge(new BigDecimal("30000"));
        depositAccountJpaRepository.save(account);

        // when
        DepositResponse response = depositService.getBalance(1L);

        // then
        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.balance()).isEqualByComparingTo("30000");
        assertThat(response.hasChargeInProgress()).isFalse();
    }

    @Test
    @DisplayName("계좌가 없으면 잔액 0원 계좌를 자동 생성한다")
    void createsAccountWithZeroBalanceForNewMember() {
        // when — 계좌 없는 회원 조회
        DepositResponse response = depositService.getBalance(999L);

        // then
        assertThat(response.memberId()).isEqualTo(999L);
        assertThat(response.balance()).isEqualByComparingTo("0");
        assertThat(response.hasChargeInProgress()).isFalse();

        // DB에도 계좌가 생겼는지 확인
        assertThat(depositAccountJpaRepository.findByMemberId(999L)).isPresent();
    }
}
