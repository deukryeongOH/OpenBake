package com.openbake.settlement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * 월 정산서에 포함된 주문 항목의 확정 상세 내역입니다.
 * (특정 월 정산서에 실제 포함된 상세 내역)
 *
 * SettlementTarget의 값을 정산서 생성 시점에 복사해 저장합니다.
 * 이후 원본 주문이나 상품 정보가 변경되더라도 과거 정산 내역은 변경되지 않습니다.
 */
@Getter
@Entity
@Table(
        name = "settlement_lines",
        uniqueConstraints = {
                /*
                 * 하나의 SettlementTarget은 하나의 정산서에만
                 * 포함될 수 있습니다.
                 */
                @UniqueConstraint(
                        name = "uk_settlement_line_target",
                        columnNames = "target_id"
                )
        },
        indexes = {
                @Index(
                        name = "idx_settlement_line_settlement",
                        columnList = "settlement_id"
                ),
                @Index(
                        name = "idx_settlement_line_order_item",
                        columnList = "order_item_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 이 상세 내역이 포함된 월 정산서 ID입니다.
     */
    @Column(name = "settlement_id", nullable = false)
    private Long settlementId;

    /**
     * 원본 정산 대상 ID입니다.
     *
     * UNIQUE 제약으로 하나의 Target이 여러 정산서에
     * 중복 포함되는 것을 방지합니다.
     */
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "order_item_id", nullable = false)
    private Long orderItemId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(
            name = "product_name_snapshot",
            nullable = false,
            length = 200
    )
    private String productNameSnapshot;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(
            name = "gross_amount",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal grossAmount;

    @Column(
            name = "commission_rate_snapshot",
            nullable = false,
            precision = 7,
            scale = 4
    )
    private BigDecimal commissionRateSnapshot;

    @Column(
            name = "commission_amount",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal commissionAmount;

    @Column(
            name = "net_amount",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal netAmount;

    @Column(name = "purchase_confirmed_at", nullable = false)
    private OffsetDateTime purchaseConfirmedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    private SettlementLine(
            Long settlementId,
            Long targetId,
            Long orderId,
            Long orderItemId,
            Long productId,
            String productNameSnapshot,
            Integer quantity,
            BigDecimal grossAmount,
            BigDecimal commissionRateSnapshot,
            BigDecimal commissionAmount,
            BigDecimal netAmount,
            OffsetDateTime purchaseConfirmedAt
    ) {
        validate(
                settlementId,
                targetId,
                orderId,
                orderItemId,
                productId,
                productNameSnapshot,
                quantity,
                grossAmount,
                commissionRateSnapshot,
                commissionAmount,
                netAmount,
                purchaseConfirmedAt
        );

        BigDecimal normalizedGrossAmount =
                normalizeMoney(grossAmount);

        BigDecimal normalizedCommissionAmount =
                normalizeMoney(commissionAmount);

        BigDecimal normalizedNetAmount =
                normalizeMoney(netAmount);

        BigDecimal normalizedCommissionRate =
                commissionRateSnapshot.setScale(
                        4,
                        RoundingMode.UNNECESSARY
                );

        validateAmountConsistency(
                normalizedGrossAmount,
                normalizedCommissionAmount,
                normalizedNetAmount
        );

        this.settlementId = settlementId;
        this.targetId = targetId;
        this.orderId = orderId;
        this.orderItemId = orderItemId;
        this.productId = productId;
        this.productNameSnapshot = productNameSnapshot.trim();
        this.quantity = quantity;
        this.grossAmount = normalizedGrossAmount;
        this.commissionRateSnapshot = normalizedCommissionRate;
        this.commissionAmount = normalizedCommissionAmount;
        this.netAmount = normalizedNetAmount;
        this.purchaseConfirmedAt = purchaseConfirmedAt;
        this.createdAt = OffsetDateTime.now();
    }

    /**
     * SettlementTarget의 스냅샷을 바탕으로 정산 상세를 생성합니다.
     *
     * 이 메서드를 사용하면 Target과 Line의 금액 및 주문 정보가
     * 동일하게 유지됩니다.
     * form(): 원본 Target을 그대로 복사하는 방식,
     * 직접 여러 금액을 전달하면 실수로 불일치 생길 수 있음
     */
    public static SettlementLine from(
            Long settlementId,
            SettlementTarget target
    ) {
        Objects.requireNonNull(
                target,
                "SettlementTarget은 필수입니다."
        );

        if (target.getId() == null) {
            throw new IllegalArgumentException(
                    "저장된 SettlementTarget만 정산 상세로 만들 수 있습니다."
            );
        }

        return new SettlementLine(
                settlementId,
                target.getId(),
                target.getOrderId(),
                target.getOrderItemId(),
                target.getProductId(),
                target.getProductNameSnapshot(),
                target.getQuantity(),
                target.getGrossAmount(),
                target.getCommissionRateSnapshot(),
                target.getCommissionAmount(),
                target.getNetAmount(),
                target.getPurchaseConfirmedAt()
        );
    }

    private static void validate(
            Long settlementId,
            Long targetId,
            Long orderId,
            Long orderItemId,
            Long productId,
            String productNameSnapshot,
            Integer quantity,
            BigDecimal grossAmount,
            BigDecimal commissionRateSnapshot,
            BigDecimal commissionAmount,
            BigDecimal netAmount,
            OffsetDateTime purchaseConfirmedAt
    ) {
        validatePositiveId(settlementId, "settlementId");
        validatePositiveId(targetId, "targetId");
        validatePositiveId(orderId, "orderId");
        validatePositiveId(orderItemId, "orderItemId");
        validatePositiveId(productId, "productId");

        if (productNameSnapshot == null
                || productNameSnapshot.isBlank()) {
            throw new IllegalArgumentException(
                    "productNameSnapshot은 필수입니다."
            );
        }

        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException(
                    "quantity는 0보다 커야 합니다."
            );
        }

        if (grossAmount == null || grossAmount.signum() < 0) {
            throw new IllegalArgumentException(
                    "grossAmount는 0 이상이어야 합니다."
            );
        }

        if (commissionRateSnapshot == null
                || commissionRateSnapshot.signum() < 0
                || commissionRateSnapshot.compareTo(
                BigDecimal.ONE
        ) > 0) {
            throw new IllegalArgumentException(
                    "commissionRateSnapshot은 0 이상 1 이하여야 합니다."
            );
        }

        if (commissionAmount == null
                || commissionAmount.signum() < 0) {
            throw new IllegalArgumentException(
                    "commissionAmount는 0 이상이어야 합니다."
            );
        }

        if (netAmount == null || netAmount.signum() < 0) {
            throw new IllegalArgumentException(
                    "netAmount는 0 이상이어야 합니다."
            );
        }

        Objects.requireNonNull(
                purchaseConfirmedAt,
                "purchaseConfirmedAt은 필수입니다."
        );
    }

    private static void validateAmountConsistency(
            BigDecimal grossAmount,
            BigDecimal commissionAmount,
            BigDecimal netAmount
    ) {
        BigDecimal calculatedGrossAmount =
                commissionAmount
                        .add(netAmount)
                        .setScale(2, RoundingMode.UNNECESSARY);

        if (grossAmount.compareTo(calculatedGrossAmount) != 0) {
            throw new IllegalArgumentException(
                    "grossAmount는 commissionAmount와 netAmount의 합과 같아야 합니다."
            );
        }
    }

    private static BigDecimal normalizeMoney(
            BigDecimal value
    ) {
        return value.setScale(
                2,
                RoundingMode.UNNECESSARY
        );
    }

    private static void validatePositiveId(
            Long value,
            String fieldName
    ) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(
                    fieldName + "는 0보다 커야 합니다."
            );
        }
    }
}