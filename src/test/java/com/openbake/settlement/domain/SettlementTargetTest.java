package com.openbake.settlement.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettlementTargetTest {

    private static final String EVENT_ID =
            "c41f55a8-9246-4bd6-bdf7-87b109fdb0c1";

    private static final Long ORDER_ID = 1001L;
    private static final Long ORDER_ITEM_ID = 2001L;
    private static final Long SELLER_ID = 10L;
    private static final Long PRODUCT_ID = 3001L;

    private static final OffsetDateTime PURCHASE_CONFIRMED_AT =
            OffsetDateTime.parse("2026-07-21T10:00:00+09:00");

    @Test
    @DisplayName("구매확정 주문 항목으로 정산 대상을 생성한다")
    void createSettlementTarget() {
        // when
        SettlementTarget target = createTarget(
                new BigDecimal("30000.00"),
                new BigDecimal("0.1000")
        );

        // then
        assertThat(target.getSourceEventId()).isEqualTo(EVENT_ID);
        assertThat(target.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(target.getOrderItemId()).isEqualTo(ORDER_ITEM_ID);
        assertThat(target.getSellerId()).isEqualTo(SELLER_ID);
        assertThat(target.getProductId()).isEqualTo(PRODUCT_ID);

        assertThat(target.getProductNameSnapshot())
                .isEqualTo("제주 당근 케이크");

        assertThat(target.getQuantity()).isEqualTo(2);

        assertThat(target.getGrossAmount())
                .isEqualByComparingTo("30000.00");

        assertThat(target.getCommissionRateSnapshot())
                .isEqualByComparingTo("0.1000");

        assertThat(target.getCommissionAmount())
                .isEqualByComparingTo("3000.00");

        assertThat(target.getNetAmount())
                .isEqualByComparingTo("27000.00");

        assertThat(target.getPurchaseConfirmedAt())
                .isEqualTo(PURCHASE_CONFIRMED_AT);

        assertThat(target.getSettlementId()).isNull();
        assertThat(target.getStatus())
                .isEqualTo(SettlementTargetStatus.PENDING);

        assertThat(target.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("수수료 계산 시 원 단위 미만 금액을 절사한다")
    void truncateCommissionBelowOneWon() {
        // given
        BigDecimal grossAmount = new BigDecimal("12345.00");
        BigDecimal commissionRate = new BigDecimal("0.1000");

        // when
        SettlementTarget target = createTarget(
                grossAmount,
                commissionRate
        );

        // then
        assertThat(target.getCommissionAmount())
                .isEqualByComparingTo("1234.00");

        assertThat(target.getNetAmount())
                .isEqualByComparingTo("11111.00");
    }

    @Test
    @DisplayName("PENDING 상태의 정산 대상을 Settlement에 배정한다")
    void assignToSettlement() {
        // given
        SettlementTarget target = createTarget(
                new BigDecimal("30000.00"),
                new BigDecimal("0.1000")
        );

        // when
        target.assignTo(100L);

        // then
        assertThat(target.getSettlementId()).isEqualTo(100L);
        assertThat(target.getStatus())
                .isEqualTo(SettlementTargetStatus.ASSIGNED);
    }

    @Test
    @DisplayName("이미 배정된 정산 대상은 다른 Settlement에 다시 배정할 수 없다")
    void cannotAssignAlreadyAssignedTarget() {
        // given
        SettlementTarget target = createTarget(
                new BigDecimal("30000.00"),
                new BigDecimal("0.1000")
        );

        target.assignTo(100L);

        // when & then
        assertThatThrownBy(() -> target.assignTo(101L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "PENDING 상태의 정산 대상만 정산서에 배정할 수 있습니다."
                );
    }

    @Test
    @DisplayName("PENDING 상태의 정산 대상을 제외할 수 있다")
    void excludePendingTarget() {
        // given
        SettlementTarget target = createTarget(
                new BigDecimal("30000.00"),
                new BigDecimal("0.1000")
        );

        // when
        target.exclude();

        // then
        assertThat(target.getStatus())
                .isEqualTo(SettlementTargetStatus.EXCLUDED);

        assertThat(target.getSettlementId()).isNull();
    }

    @Test
    @DisplayName("이미 Settlement에 배정된 정산 대상은 제외할 수 없다")
    void cannotExcludeAssignedTarget() {
        // given
        SettlementTarget target = createTarget(
                new BigDecimal("30000.00"),
                new BigDecimal("0.1000")
        );

        target.assignTo(100L);

        // when & then
        assertThatThrownBy(target::exclude)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "이미 정산서에 배정된 대상은 바로 제외할 수 없습니다."
                );
    }

    @Test
    @DisplayName("동일한 정산 대상을 여러 번 제외해도 EXCLUDED 상태를 유지한다")
    void excludeIsIdempotent() {
        // given
        SettlementTarget target = createTarget(
                new BigDecimal("30000.00"),
                new BigDecimal("0.1000")
        );

        // when
        target.exclude();
        target.exclude();

        // then
        assertThat(target.getStatus())
                .isEqualTo(SettlementTargetStatus.EXCLUDED);
    }

    @Test
    @DisplayName("eventId가 비어 있으면 정산 대상을 생성할 수 없다")
    void rejectBlankEventId() {
        assertThatThrownBy(() -> SettlementTarget.create(
                " ",
                ORDER_ID,
                ORDER_ITEM_ID,
                SELLER_ID,
                PRODUCT_ID,
                "제주 당근 케이크",
                2,
                new BigDecimal("30000.00"),
                new BigDecimal("0.1000"),
                PURCHASE_CONFIRMED_AT
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sourceEventId는 필수입니다.");
    }

    @Test
    @DisplayName("총 판매금액이 음수이면 정산 대상을 생성할 수 없다")
    void rejectNegativeGrossAmount() {
        assertThatThrownBy(() -> createTarget(
                new BigDecimal("-1000.00"),
                new BigDecimal("0.1000")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("grossAmount는 0 이상이어야 합니다.");
    }

    @Test
    @DisplayName("수수료율이 100%를 초과하면 정산 대상을 생성할 수 없다")
    void rejectCommissionRateOverOne() {
        assertThatThrownBy(() -> createTarget(
                new BigDecimal("30000.00"),
                new BigDecimal("1.1000")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "commissionRateSnapshot은 0 이상 1 이하여야 합니다."
                );
    }

    private SettlementTarget createTarget(
            BigDecimal grossAmount,
            BigDecimal commissionRate
    ) {
        return SettlementTarget.create(
                EVENT_ID,
                ORDER_ID,
                ORDER_ITEM_ID,
                SELLER_ID,
                PRODUCT_ID,
                "제주 당근 케이크",
                2,
                grossAmount,
                commissionRate,
                PURCHASE_CONFIRMED_AT
        );
    }
}