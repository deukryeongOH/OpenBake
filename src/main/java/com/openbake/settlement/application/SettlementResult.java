package com.openbake.settlement.application;

import com.openbake.settlement.domain.Settlement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record SettlementResult(
        Long settlementId,
        Long sellerId,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal grossSalesAmount,
        BigDecimal commissionAmount,
        BigDecimal netSalesAmount,
        BigDecimal adjustmentAmount,
        BigDecimal payoutAmount,
        Integer targetCount,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt
) {

    public static SettlementResult from(Settlement settlement) {
        return new SettlementResult(
                settlement.getId(),
                settlement.getSellerId(),
                settlement.getPeriodStart(),
                settlement.getPeriodEnd(),
                settlement.getGrossSalesAmount(),
                settlement.getCommissionAmount(),
                settlement.getNetSalesAmount(),
                settlement.getAdjustmentAmount(),
                settlement.getPayoutAmount(),
                settlement.getTargetCount(),
                settlement.getStatus().name(),
                settlement.getCreatedAt(),
                settlement.getCompletedAt()
        );
    }
}
