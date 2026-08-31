package com.openbake.drop.application.port;

import com.openbake.drop.application.dto.DropInfoCommand;
import com.openbake.drop.application.dto.DropInfoResult;
import com.openbake.drop.application.dto.DropProductInfoResult;

import java.util.List;

public interface ProductPort {
    DropInfoResult registerProduct(DropInfoCommand command);

    DropProductInfoResult getProductInfo(Long productId);

    List<DropProductInfoResult> findDropProductListBySellerId(Long sellerId);

    DropProductInfoResult updateProduct(Long productId, DropInfoCommand command);

    void deleteDropProduct(Long productId);

    int decreaseQuantity(Long productId, int selectQuantity);

    int rollbackQuantity(Long productId, int selectQuantity);

    // Redis 카운터 값을 product_inventory 에 반영 (절대값 대입, 멱등)
    void syncRemainQuantity(Long productId, int remainQuantity);

    int getTotalQuantity(Long productId);

    Long getSellerIdByProductId(Long productId);
}

