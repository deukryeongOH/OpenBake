package com.openbake.settlement.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * 구매 확정된 주문 항목의 정산 대상입니다.(정산 후보 원장)
 *
 * 주문 상품 하나당 하나의 SettlementTarget이 생성되며,
 * 이후 월별 정산 작업에서 특정 Settlement에 배정됩니다.
 *
 * 코드설명:상태 변경은 Setter가 아니라 assignTo()와 exclude()를 통해서만 가능하게 유지
 * 어떤 이벤트에서 생성됐는가
 * 어떤 주문 항목인가
 * 어떤 드롭 상품인가
 * 구매확정 당시 상품명이 무엇인가
 * 당시 수수료율은 얼마인가
 * 수수료는 얼마인가
 * 판매자에게 정산할 금액은 얼마인가
 *
 * 수수료 계산 정책:원 단위 미만 절사
 *
 */
@Getter
@Entity
@Table(
        name = "settlement_targets",
        uniqueConstraints = {
                // 하나의 주문 항목은 한 번만 정산 대상이 될 수 있습니다.
                @UniqueConstraint(
                        name = "uk_settlement_target_order_item",
                        columnNames = "order_item_id"
                )
        },
        indexes = {
                // 월 정산 배치에서 판매자별 미정산 대상을 조회할 때 사용합니다.
                @Index(
                        name = "idx_settlement_target_seller_status_confirmed",
                        columnList = "seller_id, status, purchase_confirmed_at"
                ),

                // 생성된 정산서에 포함된 정산 대상을 조회할 때 사용합니다.
                @Index(
                        name = "idx_settlement_target_settlement",
                        columnList = "settlement_id"
                ),

                // 원본 이벤트 추적에 사용합니다.
                @Index(
                        name = "idx_settlement_target_source_event",
                        columnList = "source_event_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 정산 대상을 생성하게 만든 구매확정 이벤트 ID입니다.
     *
     * 실제 이벤트 중복 처리는 settlement_inbox_events에서 수행하고,
     * 이 필드는 정산 원장의 추적 목적으로 저장합니다.
     */
    @Column(name = "source_event_id", nullable = false, length = 100)
    private String sourceEventId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /**
     * 팀 주문 도메인의 테이블 이름이 order_items이므로
     * orderLineId가 아니라 orderItemId로 통일합니다.
     */
    @Column(name = "order_item_id", nullable = false)
    private Long orderItemId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    /**
     * 구매확정 당시 상품명입니다.
     * 이후 드롭 상품명이 변경돼도 정산 원장은 변경되지 않습니다.
     */
    @Column(name = "product_name_snapshot", nullable = false, length = 200)
    private String productNameSnapshot;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /**
     * 수수료를 차감하기 전 총 판매 금액입니다.
     * @Column(precision):소수점을 포함한 숫자의 전체 자릿수
     * @Column(scale):소수점 아래 자리수 (기본값: 0)
     */
    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal grossAmount;

    /**
     * 구매확정 당시 적용된 수수료율입니다.
     *
     * 10%는 0.1000으로 저장합니다.
     */
    @Column(
            name = "commission_rate_snapshot",
            nullable = false,
            precision = 7,
            scale = 4
    )
    private BigDecimal commissionRateSnapshot;

    /**
     * 구매확정 시점에 계산하여 확정한 수수료 금액입니다.
     */
    @Column(
            name = "commission_amount",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal commissionAmount;

    /**
     * 판매자에게 지급할 기본 정산 금액입니다.
     *
     * grossAmount - commissionAmount
     */
    @Column(name = "net_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "purchase_confirmed_at", nullable = false)
    private OffsetDateTime purchaseConfirmedAt;

    /**
     * 월 정산 배치에서 Settlement에 포함될 때 할당됩니다.
     * 최초 생성 시에는 null입니다.
     */
    @Column(name = "settlement_id")
    private Long settlementId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SettlementTargetStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    private SettlementTarget(
            String sourceEventId,
            Long orderId,
            Long orderItemId,
            Long sellerId,
            Long productId,
            String productNameSnapshot,
            Integer quantity,
            BigDecimal grossAmount,
            BigDecimal commissionRateSnapshot,
            OffsetDateTime purchaseConfirmedAt
    ) {
        validate(
                sourceEventId,
                orderId,
                orderItemId,
                sellerId,
                productId,
                productNameSnapshot,
                quantity,
                grossAmount,
                commissionRateSnapshot,
                purchaseConfirmedAt
        );

        BigDecimal normalizedGrossAmount = grossAmount.setScale(
                2,
                RoundingMode.UNNECESSARY
        );

        BigDecimal normalizedCommissionRate = commissionRateSnapshot.setScale(
                4,
                RoundingMode.UNNECESSARY
        );

        BigDecimal calculatedCommissionAmount = calculateCommissionAmount(
                normalizedGrossAmount,
                normalizedCommissionRate
        );

        this.sourceEventId = sourceEventId;
        this.orderId = orderId;
        this.orderItemId = orderItemId;
        this.sellerId = sellerId;
        this.productId = productId;
        this.productNameSnapshot = productNameSnapshot.trim();
        this.quantity = quantity;
        this.grossAmount = normalizedGrossAmount;
        this.commissionRateSnapshot = normalizedCommissionRate;
        this.commissionAmount = calculatedCommissionAmount;
        this.netAmount = normalizedGrossAmount
                .subtract(calculatedCommissionAmount)
                .setScale(2, RoundingMode.UNNECESSARY);
        this.purchaseConfirmedAt = purchaseConfirmedAt;
        this.settlementId = null;
        this.status = SettlementTargetStatus.PENDING;
        this.createdAt = OffsetDateTime.now();
    }

    public static SettlementTarget create(
            String sourceEventId,
            Long orderId,
            Long orderItemId,
            Long sellerId,
            Long productId,
            String productNameSnapshot,
            Integer quantity,
            BigDecimal grossAmount,
            BigDecimal commissionRateSnapshot,
            OffsetDateTime purchaseConfirmedAt
    ) {
        return new SettlementTarget(
                sourceEventId,
                orderId,
                orderItemId,
                sellerId,
                productId,
                productNameSnapshot,
                quantity,
                grossAmount,
                commissionRateSnapshot,
                purchaseConfirmedAt
        );
    }

    /**
     * 월 정산 배치에서 이 정산 대상을 특정 Settlement에 배정합니다.
     */
    public void assignTo(Long settlementId) {
        validatePositiveId(settlementId, "settlementId");

        if (status != SettlementTargetStatus.PENDING) {
            throw new IllegalStateException(
                    "PENDING 상태의 정산 대상만 정산서에 배정할 수 있습니다."
            );
        }

        this.settlementId = settlementId;
        this.status = SettlementTargetStatus.ASSIGNED;
    }

    /**
     * 환불 또는 예외 사유로 정산 대상에서 제외합니다.
     */
    public void exclude() {
        if (status == SettlementTargetStatus.ASSIGNED) {
            throw new IllegalStateException(
                    "이미 정산서에 배정된 대상은 바로 제외할 수 없습니다."
            );
        }

        if (status == SettlementTargetStatus.EXCLUDED) {
            return;
        }

        this.status = SettlementTargetStatus.EXCLUDED;
    }

    /**
     * 서비스 수수료를 계산합니다.
     *
     * 정책:
     * 원 단위 미만 금액은 절사합니다.
     *
     * 예:
     * 12,345원 × 10% = 1,234.5원
     * 최종 수수료 = 1,234원
     */
    private static BigDecimal calculateCommissionAmount(
            BigDecimal grossAmount,
            BigDecimal commissionRate
    ) {

        /** 수수료 계산 정책
         * 총 판매금액: 12,345원
         * 수수료율: 10%
         *
         * 12,345 × 0.1 = 1,234.5
         * 원 단위 미만 절사 = 1,234원
         *
         * 정산금액 = 12,345 - 1,234
         *           = 11,111원
         *
         * gross_amount      = 12345.00
         * commission_amount = 1234.00
         * net_amount        = 11111.00
         */
        return grossAmount
                .multiply(commissionRate)
                .setScale(0, RoundingMode.DOWN)
                .setScale(2, RoundingMode.UNNECESSARY);
    }

    private static void validate(
            String sourceEventId,
            Long orderId,
            Long orderItemId,
            Long sellerId,
            Long productId,
            String productNameSnapshot,
            Integer quantity,
            BigDecimal grossAmount,
            BigDecimal commissionRateSnapshot,
            OffsetDateTime purchaseConfirmedAt
    ) {
        if (sourceEventId == null || sourceEventId.isBlank()) {
            throw new IllegalArgumentException(
                    "sourceEventId는 필수입니다."
            );
        }
        Objects.requireNonNull(purchaseConfirmedAt, "purchaseConfirmedAt은 필수입니다.");

        validatePositiveId(orderId, "orderId");
        validatePositiveId(orderItemId, "orderItemId");
        validatePositiveId(sellerId, "sellerId");
        validatePositiveId(productId, "productId");

        if (productNameSnapshot == null || productNameSnapshot.isBlank()) {
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
                || commissionRateSnapshot.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    "commissionRateSnapshot은 0 이상 1 이하여야 합니다."
            );
        }
    }

    private static void validatePositiveId(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(
                    fieldName + "는 0보다 커야 합니다."
            );
        }
    }
}