package com.openbake.product.application.dto;

import com.openbake.product.domain.Category;
import com.openbake.product.domain.Type;

import java.time.LocalDate;
import java.util.Set;

public record ProductInfoResult(
        String name,
        String description,
        String imageUrl,
        int totalQuantity,
        int price,
        Set<LocalDate> pickUpAvailableDates,
        Category category,
        Long productId,
        int remainQuantity,
        Type type,
        Long sellerId
) {
    public static ProductInfoResult of(String name, String description, String imageUrl, int totalQuantity, int price, Set<LocalDate> pickupDates, Category category, Long productId, int remainQuantity, Type type, Long sellerId) {
        return new ProductInfoResult(name, description, imageUrl, totalQuantity, price, pickupDates, category, productId, remainQuantity, type, sellerId);
    }
}
