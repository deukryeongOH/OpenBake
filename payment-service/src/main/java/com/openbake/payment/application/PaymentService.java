package com.openbake.payment.application;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.payment.domain.AccountType;
import com.openbake.payment.domain.DepositAccount;
import com.openbake.payment.domain.OrderPayment;
import com.openbake.payment.domain.ReferenceType;
import com.openbake.payment.domain.TransactionType;
import com.openbake.payment.domain.WalletTransaction;
import com.openbake.payment.domain.DepositAccountRepository;
import com.openbake.payment.domain.OrderPaymentRepository;
import com.openbake.payment.domain.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final DepositAccountRepository depositAccountRepository;
    private final OrderPaymentRepository orderPaymentRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    /**
     * 주문 결제 — 주문 서비스가 같은 트랜잭션 안에서 직접 호출한다.
     * 1. 회원 예치금 계좌에서 금액 차감 (잔액 부족 시 예외)
     * 2. OrderPayment 생성 (상태: PAID)
     * 3. 회원 원장에 -금액 기록 (돈 나감)
     * 4. 플랫폼 원장에 +금액 기록 (돈 들어옴)
     */
    @Transactional
    public void pay(Long orderId, Long memberId, BigDecimal amount) {
        DepositAccount memberAccount = getOrCreateMemberAccount(memberId);
        DepositAccount platformAccount = getPlatformAccount();

        // 회원 예치금 차감 — 잔액 부족하면 BusinessException(INSUFFICIENT_BALANCE)
        memberAccount.deduct(amount);

        // 결제 기록 생성
        OrderPayment payment = OrderPayment.create(orderId, memberId, amount);
        orderPaymentRepository.save(payment);

        // 회원 원장: -금액 (negate()로 부호 반전)
        walletTransactionRepository.save(WalletTransaction.create(
                memberAccount,
                TransactionType.PAYMENT,
                amount.negate(),
                memberAccount.getBalance(),
                ReferenceType.ORDER_PAYMENT,
                payment.getId()
        ));

        // 플랫폼 원장: +금액
        walletTransactionRepository.save(WalletTransaction.create(
                platformAccount,
                TransactionType.PAYMENT,
                amount,
                null,
                // 플랫폼 계좌는 거래 내역만 기록하는 게 목적이라 잔액 추적. 멤버 계좌처럼 잔액 부족 시 결제를 막는다는 개념이 아니기 때문.
                ReferenceType.ORDER_PAYMENT,
                payment.getId()
        ));
    }

    /**
     * 주문 환불 — pay()의 역방향. PAID 상태일 때만 가능.
     * 1. OrderPayment 상태를 REFUNDED로 변경
     * 2. 회원 예치금 계좌에 금액 복구
     * 3. 회원 원장에 +금액 기록 (돈 돌아옴)
     * 4. 플랫폼 원장에 -금액 기록 (돈 나감)
     */
    @Transactional
    public void refund(Long orderId) {
        OrderPayment payment = orderPaymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        // PAID가 아니면 예외 발생
        payment.refund();

        DepositAccount memberAccount = depositAccountRepository.findByMemberIdForUpdate(payment.getMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPOSIT_ACCOUNT_NOT_FOUND));
        DepositAccount platformAccount = getPlatformAccount();

        // 회원 예치금 복구
        memberAccount.refund(payment.getAmount());

        // 회원 원장: +금액 (돈 돌아옴)
        walletTransactionRepository.save(WalletTransaction.create(
                memberAccount,
                TransactionType.REFUND,
                payment.getAmount(),
                memberAccount.getBalance(),
                ReferenceType.ORDER_PAYMENT,
                payment.getId()
        ));

        // 플랫폼 원장: -금액 (돈 나감)
        walletTransactionRepository.save(WalletTransaction.create(
                platformAccount,
                TransactionType.REFUND,
                payment.getAmount().negate(),
                null,  // 플랫폼은 잔액 추적 안 함
                ReferenceType.ORDER_PAYMENT,
                payment.getId()
        ));
    }

    /**
     * 구매 확정 — PAID → CONFIRMED. 정산 대상이 된다.
     * 환불 불가 시점은 드롭 마감 기준이며, 주문 도메인에서 판단한다.
     */
    @Transactional
    public void confirmPayment(Long orderId) {
        OrderPayment payment = orderPaymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        // PAID가 아니면 예외 발생
        payment.confirm();
    }

    // 회원 계좌 조회 (비관적 락) — 잔액 변경 시 Lost Update 방지
    private DepositAccount getOrCreateMemberAccount(Long memberId) {
        return depositAccountRepository.findByMemberIdForUpdate(memberId)
                .orElseGet(() -> depositAccountRepository.save(
                        DepositAccount.createMemberAccount(memberId)
                ));
    }

    // 플랫폼 계좌 조회. 시스템에 1개만 존재하는 수익 집계용 가상 계좌
    private DepositAccount getPlatformAccount() {
        return depositAccountRepository.findByAccountType(AccountType.PLATFORM)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLATFORM_ACCOUNT_NOT_FOUND));
    }
}
