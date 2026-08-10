package com.openbake.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 예치금 계좌.
 * - MEMBER 계좌: 회원당 1개. 잔액(balance)을 추적하며 충전/차감/환불이 가능.
 * - PLATFORM 계좌: 시스템에 1개. 잔액 추적 없이 거래 내역만 기록하는 수익 집계용 가상 계좌.
 */
@Entity
@Table(name = "deposit_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DepositAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType accountType;  // MEMBER 또는 PLATFORM

    @Column(unique = true)
    private Long memberId;  // PLATFORM이면 null

    private BigDecimal balance;  // MEMBER만 유효, PLATFORM은 null

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 회원 계좌 생성 — 잔액 0원으로 시작
    public static DepositAccount createMemberAccount(Long memberId) {
        DepositAccount account = new DepositAccount();
        account.accountType = AccountType.MEMBER;
        account.memberId = memberId;
        account.balance = BigDecimal.ZERO;
        account.createdAt = LocalDateTime.now();
        return account;
    }

    // 플랫폼 계좌 생성 — 잔액 없음 (거래 내역만 기록)
    public static DepositAccount createPlatformAccount() {
        DepositAccount account = new DepositAccount();
        account.accountType = AccountType.PLATFORM;
        account.memberId = null;
        account.balance = null;
        account.createdAt = LocalDateTime.now();
        return account;
    }

    // PG 충전 시 잔액 증가
    public void charge(BigDecimal amount) {
        validateMemberAccount();
        validatePositiveAmount(amount);
        this.balance = this.balance.add(amount);
    }

    // 주문 결제 시 잔액 차감 — 잔액 부족하면 예외
    public void deduct(BigDecimal amount) {
        validateMemberAccount();
        validatePositiveAmount(amount);
        if (this.balance.compareTo(amount) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
        }
        this.balance = this.balance.subtract(amount);
    }

    // 주문 취소 시 잔액 복구 — charge()와 동작은 같지만 의미가 다름
    public void refund(BigDecimal amount) {
        validateMemberAccount();
        validatePositiveAmount(amount);
        this.balance = this.balance.add(amount);
    }

    // PLATFORM 계좌는 잔액 변경 불가
    private void validateMemberAccount() {
        if (this.accountType != AccountType.MEMBER) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "MEMBER 계정만 잔액 변경이 가능합니다.");
        }
    }

    private void validatePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "금액은 0보다 커야 합니다.");
        }
    }
}
