package com.openbake.order.application.port;

import com.openbake.order.application.port.dto.SellerInfo;

import java.util.Optional;

public interface SellerPort {
    Optional<Long> getCurrentSellerId();

    Optional<SellerInfo> findSeller(Long sellerId);
}