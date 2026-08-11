package com.openbake.product.application.dto;

import com.openbake.product.domain.Category;

import java.time.LocalDate;
import java.util.Set;

public record GeneralProductInfoCommand(String name, String description, String imageUrl, int totalQuantity, int price, Set<LocalDate> pickupDates, Category category) {
    public static GeneralProductInfoCommand create(String name, String description, String imageUrl, int totalQuantity, int price, Set<LocalDate> pickupDates, Category category) {
        return new GeneralProductInfoCommand(name, description, imageUrl, totalQuantity, price, pickupDates, category);
    }
}
