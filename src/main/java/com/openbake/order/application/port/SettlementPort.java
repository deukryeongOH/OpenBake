package com.openbake.order.application.port;

import com.openbake.order.application.port.dto.PurchaseConfirmedInfo;

public interface SettlementPort {
    void publishPurchaseConfirmed(PurchaseConfirmedInfo info);
}
