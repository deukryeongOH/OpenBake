package com.openbake.settlement.domain;

import com.openbake.seller.domain.SettlementAccountConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Entity
@Table(
        name = "settlement_payouts",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_settlement_payout_idempotency_key",
                        columnNames = "idempotency_key"
                )
        },
        indexes = {
                @Index(
                        name = "idx_settlement_payout_settlement_id",
                        columnList = "settlement_id"
                ),
                @Index(
                        name = "idx_settlement_payout_status",
                        columnList = "status"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementPayout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "settlement_id", nullable = false)
    private Long settlementId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "payout_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal payoutAmount;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SettlementPayoutStatus status;

    @Column(
            name = "bank_code_snapshot",
            nullable = false,
            length = 20
    )
    private String bankCodeSnapshot;

    @Convert(converter = SettlementAccountConverter.class)
    @Column(
            name = "account_number_snapshot",
            nullable = false,
            length = 500
    )
    private String accountNumberSnapshot;

    @Convert(converter = SettlementAccountConverter.class)
    @Column(
            name = "account_holder_snapshot",
            nullable = false,
            length = 500
    )
    private String accountHolderSnapshot;

    @Column(name = "external_transaction_id", length = 100)
    private String externalTransactionId;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "failed_at")
    private OffsetDateTime failedAt;

    public static SettlementPayout create(
            Long settlementId,
            Long sellerId,
            BigDecimal payoutAmount,
            String idempotencyKey,
            String bankCode,
            String accountNumber,
            String accountHolder
    ) {
        validateIdempotencyKey(idempotencyKey);
        validatePayoutAmount(payoutAmount);
        validateAccount(
                bankCode,
                accountNumber,
                accountHolder
        );

        SettlementPayout payout =
                new SettlementPayout();

        payout.settlementId = settlementId;
        payout.sellerId = sellerId;
        payout.payoutAmount = payoutAmount;
        payout.idempotencyKey = idempotencyKey;

        payout.bankCodeSnapshot = bankCode;
        payout.accountNumberSnapshot = accountNumber;
        payout.accountHolderSnapshot = accountHolder;

        payout.status =
                SettlementPayoutStatus.REQUESTED;

        payout.requestedAt =
                OffsetDateTime.now();

        return payout;
    }

    public void startProcessing() {
        if (status != SettlementPayoutStatus.REQUESTED) {
            throw new IllegalStateException(
                    "처리 중 상태로 변경할 수 없는 지급 상태입니다. status=" + status
            );
        }

        status = SettlementPayoutStatus.PROCESSING;
    }

    public void complete(String externalTransactionId) {
        if (status != SettlementPayoutStatus.PROCESSING) {
            throw new IllegalStateException(
                    "완료 처리할 수 없는 지급 상태입니다. status=" + status
            );
        }

        if (externalTransactionId == null || externalTransactionId.isBlank()) {
            throw new IllegalArgumentException(
                    "외부 거래 ID는 필수입니다."
            );
        }

        status = SettlementPayoutStatus.COMPLETED;
        this.externalTransactionId = externalTransactionId;
        this.completedAt = OffsetDateTime.now();
        this.failureReason = null;
    }

    public void fail(String failureReason) {
        if (status != SettlementPayoutStatus.PROCESSING) {
            throw new IllegalStateException(
                    "실패 처리할 수 없는 지급 상태입니다. status=" + status
            );
        }

        if (failureReason == null || failureReason.isBlank()) {
            throw new IllegalArgumentException(
                    "지급 실패 사유는 필수입니다."
            );
        }

        status = SettlementPayoutStatus.FAILED;
        this.failureReason = failureReason;
        this.failedAt = OffsetDateTime.now();
    }

    private static void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "멱등키는 필수입니다."
            );
        }
    }

    private static void validatePayoutAmount(
            BigDecimal payoutAmount
    ) {
        if (payoutAmount == null
                || payoutAmount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "지급 금액은 0보다 커야 합니다."
            );
        }
    }

    private static void validateAccount(
            String bankCode,
            String accountNumber,
            String accountHolder
    ) {
        if (bankCode == null || bankCode.isBlank()) {
            throw new IllegalArgumentException(
                    "은행 코드는 필수입니다."
            );
        }

        if (accountNumber == null
                || accountNumber.isBlank()) {
            throw new IllegalArgumentException(
                    "계좌번호는 필수입니다."
            );
        }

        if (accountHolder == null
                || accountHolder.isBlank()) {
            throw new IllegalArgumentException(
                    "예금주명은 필수입니다."
            );
        }
    }
}