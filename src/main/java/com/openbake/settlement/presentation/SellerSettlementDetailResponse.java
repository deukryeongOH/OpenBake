package com.openbake.settlement.presentation;

import com.openbake.settlement.application.SellerSettlementDetailResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record SellerSettlementDetailResponse(
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

    public static SellerSettlementDetailResponse from(
            SellerSettlementDetailResult result
    ) {
        List<Line> lines = result.lines()
                .stream()
                .map(Line::from)
                .toList();

        return new SellerSettlementDetailResponse(
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
                result.completedAt(),
                result.failureReason(),
                result.failedAt(),
                lines
        );
    }

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

        private static Line from(
                SellerSettlementDetailResult.Line line
        ) {
            return new Line(
                    line.settlementLineId(),
                    line.targetId(),
                    line.orderId(),
                    line.orderItemId(),
                    line.productId(),
                    line.productName(),
                    line.quantity(),
                    line.grossAmount(),
                    line.commissionRate(),
                    line.commissionAmount(),
                    line.netAmount(),
                    line.purchaseConfirmedAt()
            );
        }
    }
}