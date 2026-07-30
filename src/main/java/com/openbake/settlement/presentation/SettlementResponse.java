package com.openbake.settlement.presentation;

import com.openbake.settlement.application.SettlementResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record SettlementResponse(
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

    public static SettlementResponse from(SettlementResult result) {
        return new SettlementResponse(
                result.settlementId(),
                result.sellerId(),
                result.periodStart(),
                result.periodEnd(),
                result.grossSalesAmount(),
                result.commissionAmount(),
                result.netSalesAmount(),
                result.adjustmentAmount(),
                result.payoutAmount(),
                result.targetCount(),
                result.status(),
                result.createdAt(),
                result.completedAt()
        );
    }
}
