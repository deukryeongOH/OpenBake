package com.openbake.product.application.dto;

import com.openbake.product.domain.Category;
import com.openbake.product.domain.Type;

import java.time.LocalDate;
import java.util.Set;

public record GeneralProductInfoCommand(String name, String description, String imageUrl, int totalQuantity, int price, Set<LocalDate> pickupDates, Category category, Type type) {
    public static GeneralProductInfoCommand create(String name, String description, String imageUrl, int totalQuantity, int price, Set<LocalDate> pickupDates, Category category, Type type) {
        return new GeneralProductInfoCommand(name, description, imageUrl, totalQuantity, price, pickupDates, category, type);
    }
}
