package com.openbake.settlement.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record SellerSettlementDetailResult(
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
        OffsetDateTime completedAt,
        String failureReason,
        OffsetDateTime failedAt,
        List<Line> lines
) {

    public record Line(
            Long settlementLineId,
            Long targetId,
            Long orderId,
            Long orderItemId,
            Long productId,
            String productName,
            Integer quantity,
            BigDecimal grossAmount,
            BigDecimal commissionRate,
            BigDecimal commissionAmount,
            BigDecimal netAmount,
            OffsetDateTime purchaseConfirmedAt
    ) {
    }
}