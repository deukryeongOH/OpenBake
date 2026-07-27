package com.openbake.settlement.application.port;

public record SellerSettlementAccountSnapshot(
        Long sellerId,
        String bankCode,
        String accountNumber,
        String accountHolder
) {
    public SellerSettlementAccountSnapshot {
        if (sellerId == null || sellerId <= 0) {
            throw new IllegalArgumentException(
                    "sellerId는 0보다 커야 합니다."
            );
        }

        if (bankCode == null || bankCode.isBlank()) {
            throw new IllegalStateException(
                    "정산 은행 코드가 등록되지 않았습니다."
            );
        }

        if (accountNumber == null || accountNumber.isBlank()) {
            throw new IllegalStateException(
                    "정산 계좌번호가 등록되지 않았습니다."
            );
        }

        if (accountHolder == null || accountHolder.isBlank()) {
            throw new IllegalStateException(
                    "정산 예금주명이 등록되지 않았습니다."
            );
        }
    }
}