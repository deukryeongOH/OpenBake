package com.openbake.drop.application.dto;

import com.openbake.product.application.dto.ProductInfoResult;
import com.openbake.product.domain.Category;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

public record RegisterDropResult(String name, String description, String image, LocalDateTime dropStart, LocalDateTime dropEnd,
                                 int totalQuantity, int limitQuantity, int price, Set<LocalDate> pickupDates, Category category, Long productId) {
    public static RegisterDropResult of(ProductInfoResult result, LocalDateTime dropStart, LocalDateTime dropEnd, int limitQuantity){
        return new RegisterDropResult(result.name(), result.description(), result.imageUrl(), dropStart, dropEnd, result.totalQuantity(), limitQuantity, result.price(), result.pickUpAvailableDates(), result.category(), result.productId());
    }
}
