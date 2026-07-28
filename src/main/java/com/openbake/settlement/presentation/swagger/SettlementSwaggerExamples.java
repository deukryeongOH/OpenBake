package com.openbake.settlement.presentation.swagger;

public final class SettlementSwaggerExamples {

    private SettlementSwaggerExamples() {
    }

    public static final String PURCHASE_CONFIRMED_REQUEST_1 = """
            {
              "eventId": "purchase-confirmed-order-item-2001",
              "orderId": 1001,
              "orderItemId": 2001,
              "sellerId": 1,
              "dropId": 3001,
              "productNameSnapshot": "제주 당근 케이크",
              "quantity": 2,
              "grossAmount": 30000,
              "purchaseConfirmedAt": "2026-07-15T10:00:00+09:00"
            }
            """;

    public static final String PURCHASE_CONFIRMED_REQUEST_2 = """
            {
              "eventId": "purchase-confirmed-order-item-2002",
              "orderId": 1002,
              "orderItemId": 2002,
              "sellerId": 1,
              "dropId": 3002,
              "productNameSnapshot": "제주 감귤 타르트",
              "quantity": 1,
              "grossAmount": 20000,
              "purchaseConfirmedAt": "2026-07-20T14:00:00+09:00"
            }
            """;

    public static final String PURCHASE_CONFIRMED_SUCCESS_RESPONSE = """
            {
              "success": true,
              "data": {
                "eventId": "purchase-confirmed-order-item-2001",
                "settlementTargetId": 1,
                "duplicate": false
              }
            }
            """;

    public static final String PURCHASE_CONFIRMED_DUPLICATE_RESPONSE = """
            {
              "success": true,
              "data": {
                "eventId": "purchase-confirmed-order-item-2001",
                "settlementTargetId": 1,
                "duplicate": true
              }
            }
            """;

    public static final String MONTHLY_BATCH_REQUEST = """
            {
              "periodStart": "2026-07-01",
              "periodEnd": "2026-08-01"
            }
            """;

    public static final String MONTHLY_BATCH_RESPONSE = """
            {
              "success": true,
              "data": {
                "jobExecutionId": 1,
                "jobName": "monthlySettlementJob",
                "status": "STARTING",
                "periodStart": "2026-07-01",
                "periodEnd": "2026-08-01"
              }
            }
            """;

    public static final String SELLER_SETTLEMENT_LIST_RESPONSE = """
            {
              "success": true,
              "data": {
                "settlements": [
                  {
                    "settlementId": 1,
                    "periodStart": "2026-07-01",
                    "periodEnd": "2026-08-01",
                    "grossSalesAmount": 50000,
                    "commissionAmount": 5000,
                    "adjustmentAmount": 0,
                    "payoutAmount": 45000,
                    "targetCount": 2,
                    "status": "READY"
                  }
                ]
              }
            }
            """;

    public static final String PAYOUT_START_REQUEST = """
            {
              "idempotencyKey": "settlement-1-payout-1"
            }
            """;

    public static final String PAYOUT_START_RESPONSE = """
            {
              "success": true,
              "data": {
                "payoutId": 1,
                "settlementId": 1,
                "sellerId": 10,
                "payoutAmount": 45000,
                "idempotencyKey": "settlement-1-payout-1",
                "status": "PROCESSING",
                "requestedAt": "2026-07-23T15:00:00+09:00"
              }
            }
            """;

    public static final String PAYOUT_COMPLETE_REQUEST = """
            {
              "externalTransactionId": "bank-transfer-20260723-0001"
            }
            """;

    public static final String PAYOUT_FAIL_REQUEST = """
            {
              "failureReason": "판매자 계좌 정보가 올바르지 않습니다."
            }
            """;

    public static final String PAYOUT_COMPLETE_RESPONSE = """
            {
              "success": true,
              "data": {
                "payoutId": 1,
                "settlementId": 1,
                "sellerId": 10,
                "payoutAmount": 45000,
                "idempotencyKey": "settlement-1-payout-1",
                "status": "COMPLETED",
                "externalTransactionId": "bank-transfer-20260723-0001",
                "requestedAt": "2026-07-23T15:00:00+09:00",
                "completedAt": "2026-07-23T15:01:00+09:00"
              }
            }
            """;

    public static final String PAYOUT_FAIL_RESPONSE = """
            {
              "success": true,
              "data": {
                "payoutId": 2,
                "settlementId": 2,
                "sellerId": 10,
                "payoutAmount": 45000,
                "idempotencyKey": "settlement-2-payout-1",
                "status": "FAILED",
                "failureReason": "판매자 계좌 정보가 올바르지 않습니다.",
                "requestedAt": "2026-07-23T15:00:00+09:00",
                "failedAt": "2026-07-23T15:01:00+09:00"
              }
            }
            """;
}