package com.openbake.drop.application.dto;



import com.openbake.product.domain.Category;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

public record DropProductInfoResult(String name, String description, String imageUrl,
                                    Set<LocalDate> pickUpAvailableDates,
                                    int price, int totalQuantity, int remainQuantity,
                                    Long sellerId, Long productId, Category category
                                    ) {
    public static DropProductInfoResult of(String name, String description, String imageUrl,
                                           Set<LocalDate> pickUpAvailableDates,
                                           int price, int totalQuantity, int remainQuantity,
                                           Long sellerId, Long productId, Category category) {
        return new DropProductInfoResult(name, description, imageUrl, new HashSet<>(pickUpAvailableDates), price, totalQuantity, remainQuantity, sellerId, productId, category);
    }
}
