package com.openbake.product.presentation.dto;

import com.openbake.product.application.dto.ProductInfoResult;
import com.openbake.product.domain.Category;
import com.openbake.product.domain.Type;

import java.time.LocalDate;
import java.util.Set;

public record ProductInfoResponse(
        String name,
        String description,
        String imageUrl,
        int totalQuantity,
        int price,
        Set<LocalDate> pickUpAvailableDates,
        Category category, Long productId, int remainQuantity, Type type
) {
    public static ProductInfoResponse of(ProductInfoResult result) {
        return new ProductInfoResponse(result.name(), result.description(), result.imageUrl(), result.totalQuantity(), result.price(), result.pickUpAvailableDates(), result.category(), result.productId(), result.remainQuantity(), result.type());
    }
}
