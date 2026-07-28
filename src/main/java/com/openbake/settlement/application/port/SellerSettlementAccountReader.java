package com.openbake.settlement.application.port;

public interface SellerSettlementAccountReader {

    SellerSettlementAccountSnapshot getAccount(
            Long sellerId
    );
}