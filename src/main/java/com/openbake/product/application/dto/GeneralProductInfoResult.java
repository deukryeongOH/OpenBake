package com.openbake.product.application.dto;

import com.openbake.product.domain.Category;
import com.openbake.product.domain.Type;

import java.time.LocalDate;
import java.util.Set;

public record GeneralProductInfoResult(
        String name,
        String description,
        String imageUrl,
        int totalQuantity,
        int price,
        Set<LocalDate> pickUpAvailableDates,
        Category category,
        Long productId,
        int remainQuantity,
        Type type
) {
    public static GeneralProductInfoResult of(GeneralProductInfoCommand command, Long productId, int remainQuantity, Type type) {
        return new GeneralProductInfoResult(command.name(), command.description(), command.imageUrl(), command.totalQuantity(), command.price(), command.pickupDates(), command.category(), productId, remainQuantity, type);
    }
}
