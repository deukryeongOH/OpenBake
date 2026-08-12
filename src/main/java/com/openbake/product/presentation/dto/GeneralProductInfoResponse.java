package com.openbake.product.presentation.dto;

import com.openbake.product.application.dto.GeneralProductInfoResult;
import com.openbake.product.domain.Category;
import com.openbake.product.domain.Type;

import java.time.LocalDate;
import java.util.Set;

public record GeneralProductInfoResponse(
        String name,
        String description,
        String imageUrl,
        int totalQuantity,
        int price,
        Set<LocalDate> pickUpAvailableDates,
        Category category, Long productId, int remainQuantity, Type type
) {
    public static GeneralProductInfoResponse of(GeneralProductInfoResult result) {
        return new GeneralProductInfoResponse(result.name(), result.description(), result.imageUrl(), result.totalQuantity(), result.price(), result.pickUpAvailableDates(), result.category(), result.productId(), result.remainQuantity(), result.type());
    }
}
