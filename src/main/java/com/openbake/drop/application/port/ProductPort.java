package com.openbake.drop.application.port;

import com.openbake.drop.application.dto.DropInfoCommand;
import com.openbake.drop.application.dto.DropInfoResult;
import com.openbake.drop.application.dto.DropProductInfoResult;

import java.util.List;

public interface ProductPort {
    DropInfoResult registerProduct(DropInfoCommand command);

    DropProductInfoResult getProductInfo(Long productId);

    List<DropProductInfoResult> findProductListBySellerId(Long sellerId);

    DropProductInfoResult updateProduct(Long productId, DropInfoCommand command);

    void deleteDropProduct(Long productId);

    int decreaseQuantity(Long productId, int selectQuantity);

    int rollbackQuantity(Long productId, int selectQuantity);

    Long getSellerIdByProductId(Long productId);

    boolean isGeneralProduct(Long aLong);
}

