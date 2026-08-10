package com.openbake.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 거래 내역 원장.
 * 모든 돈의 이동을 기록한다. 은행 통장 거래내역처럼 한 번 쓰면 수정/삭제하지 않는다.
 * 잘못된 거래는 새로운 거래(환불 등)를 추가해서 상쇄한다.
 */
@Entity
@Table(name = "wallet_transactions", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_wallet_tx_account_ref",
                columnNames = {"deposit_account_id", "reference_type", "reference_id", "transaction_type"}
        )
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 이 거래가 발생한 계좌 (회원 또는 플랫폼)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deposit_account_id", nullable = false)
    private DepositAccount depositAccount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType transactionType;

    // 부호 있는 금액. +면 돈 들어옴, -면 돈 나감
    @Column(nullable = false)
    private BigDecimal amount;

    // 거래 후 잔액. MEMBER만 기록, PLATFORM은 null
    private BigDecimal balanceAfter;

    // 이 거래의 원인 유형 — "무슨 종류(referenceType)의 몇 번(referenceId)"으로 원인을 추적
    // 실제 FK가 아니라 애플리케이션 레벨의 다형성 참조
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReferenceType referenceType;

    @Column(nullable = false)
    private Long referenceId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static WalletTransaction create(DepositAccount account,
                                           TransactionType transactionType,
                                           BigDecimal amount,
                                           BigDecimal balanceAfter,
                                           ReferenceType referenceType,
                                           Long referenceId) {
        WalletTransaction tx = new WalletTransaction();
        tx.depositAccount = account;
        tx.transactionType = transactionType;
        tx.amount = amount;
        tx.balanceAfter = balanceAfter;
        tx.referenceType = referenceType;
        tx.referenceId = referenceId;
        tx.createdAt = LocalDateTime.now();
        return tx;
    }
}
