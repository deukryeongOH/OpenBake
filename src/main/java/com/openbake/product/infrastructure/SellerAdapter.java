package com.openbake.product.infrastructure;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.product.application.port.CurrentSellerPort;
import com.openbake.seller.application.CurrentSellerProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SellerAdapter implements CurrentSellerPort {

    private final CurrentSellerProvider currentSellerProvider;

    @Override
    public Long getCurrentSellerId() {
        return currentSellerProvider.getSellerId().orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATE));
    }
}
