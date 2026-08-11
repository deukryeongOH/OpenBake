package com.openbake.drop.infrastructure.adapter;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.drop.infrastructure.port.CurrentSellerPort;
import com.openbake.seller.application.CurrentSellerProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component("dropSellerAdapter")
@RequiredArgsConstructor
public class SellerAdapter implements CurrentSellerPort {

    private final CurrentSellerProvider currentSellerProvider;

    @Override
    public Long getCurrentSellerId() {
        return currentSellerProvider.getSellerId().orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATE));
    }
}
