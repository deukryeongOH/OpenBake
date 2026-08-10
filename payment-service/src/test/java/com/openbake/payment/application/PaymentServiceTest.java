package com.openbake.payment.application;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.payment.domain.AccountType;
import com.openbake.payment.domain.DepositAccount;
import com.openbake.payment.domain.OrderPayment;
import com.openbake.payment.domain.PaymentStatus;
import com.openbake.payment.domain.WalletTransaction;
import com.openbake.payment.infrastructure.DepositAccountJpaRepository;
import com.openbake.payment.infrastructure.OrderPaymentJpaRepository;
import com.openbake.payment.infrastructure.WalletTransactionJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaymentServiceTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private DepositAccountJpaRepository depositAccountJpaRepository;

    @Autowired
    private OrderPaymentJpaRepository orderPaymentJpaRepository;

    @Autowired
    private WalletTransactionJpaRepository walletTransactionJpaRepository;

    private DepositAccount memberAccount;
    private DepositAccount platformAccount;

    @BeforeEach
    void setUp() {
        memberAccount = depositAccountJpaRepository.save(DepositAccount.createMemberAccount(1L));
        memberAccount.charge(new BigDecimal("50000"));
        depositAccountJpaRepository.save(memberAccount);

        platformAccount = depositAccountJpaRepository.findByAccountType(AccountType.PLATFORM)
                .orElseGet(() -> depositAccountJpaRepository.save(DepositAccount.createPlatformAccount()));
    }

    @Nested
    @DisplayName("pay()")
    class Pay {

        @Test
        @DisplayName("정상 결제 시 잔액이 차감된다")
        void deductsBalance() {
            paymentService.pay(100L, 1L, new BigDecimal("5000"));

            DepositAccount account = depositAccountJpaRepository.findByMemberId(1L).get();
            assertThat(account.getBalance()).isEqualByComparingTo("45000");
        }

        @Test
        @DisplayName("정상 결제 시 OrderPayment가 PAID 상태로 생성된다")
        void createsOrderPayment() {
            paymentService.pay(100L, 1L, new BigDecimal("5000"));

            OrderPayment payment = orderPaymentJpaRepository.findByOrderId(100L).get();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(payment.getAmount()).isEqualByComparingTo("5000");
        }

        @Test
        @DisplayName("정상 결제 시 거래 내역이 2건 생성된다 (회원 -, 플랫폼 +)")
        void createsWalletTransactions() {
            paymentService.pay(100L, 1L, new BigDecimal("5000"));

            List<WalletTransaction> transactions = walletTransactionJpaRepository.findAll();
            assertThat(transactions).hasSize(2);

            WalletTransaction memberTx = transactions.stream()
                    .filter(tx -> tx.getDepositAccount().getId().equals(memberAccount.getId()))
                    .findFirst().get();
            assertThat(memberTx.getAmount()).isEqualByComparingTo("-5000");
            assertThat(memberTx.getBalanceAfter()).isEqualByComparingTo("45000");

            WalletTransaction platformTx = transactions.stream()
                    .filter(tx -> tx.getDepositAccount().getId().equals(platformAccount.getId()))
                    .findFirst().get();
            assertThat(platformTx.getAmount()).isEqualByComparingTo("5000");
            assertThat(platformTx.getBalanceAfter()).isNull();
        }

        @Test
        @DisplayName("잔액 부족 시 예외가 발생한다")
        void failsWhenInsufficientBalance() {
            assertThatThrownBy(() ->
                    paymentService.pay(100L, 1L, new BigDecimal("60000"))
            ).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);
        }
    }

    @Nested
    @DisplayName("refund()")
    class Refund {

        @Test
        @DisplayName("정상 환불 시 잔액이 복구된다")
        void restoresBalance() {
            paymentService.pay(100L, 1L, new BigDecimal("5000"));
            paymentService.refund(100L);

            DepositAccount account = depositAccountJpaRepository.findByMemberId(1L).get();
            assertThat(account.getBalance()).isEqualByComparingTo("50000");
        }

        @Test
        @DisplayName("정상 환불 시 OrderPayment 상태가 REFUNDED가 된다")
        void statusBecomesRefunded() {
            paymentService.pay(100L, 1L, new BigDecimal("5000"));
            paymentService.refund(100L);

            OrderPayment payment = orderPaymentJpaRepository.findByOrderId(100L).get();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        }

        @Test
        @DisplayName("PAID가 아닌 상태에서 환불하면 예외가 발생한다")
        void failsWhenNotPaid() {
            paymentService.pay(100L, 1L, new BigDecimal("5000"));
            paymentService.confirmPayment(100L);

            assertThatThrownBy(() ->
                    paymentService.refund(100L)
            ).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_PAYMENT_STATUS);
        }
    }

    @Nested
    @DisplayName("confirmPayment()")
    class ConfirmPayment {

        @Test
        @DisplayName("정상 확정 시 PAID에서 CONFIRMED로 변경된다")
        void statusBecomesConfirmed() {
            paymentService.pay(100L, 1L, new BigDecimal("5000"));
            paymentService.confirmPayment(100L);

            OrderPayment payment = orderPaymentJpaRepository.findByOrderId(100L).get();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        }

        @Test
        @DisplayName("PAID가 아닌 상태에서 확정하면 예외가 발생한다")
        void failsWhenNotPaid() {
            paymentService.pay(100L, 1L, new BigDecimal("5000"));
            paymentService.refund(100L);

            assertThatThrownBy(() ->
                    paymentService.confirmPayment(100L)
            ).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_PAYMENT_STATUS);
        }
    }
}
