package com.openbake.drop.application.dto;

import com.openbake.drop.domain.DropProduct;

import java.time.LocalDate;
import java.util.Set;

public record ConfirmEntryResponse(
    String name, String description, String imageUrl, int price, int limitQuantity, int remainQuantity, Set<LocalDate> pickupDates
) {

    public static ConfirmEntryResponse of(DropProduct dropProduct, int limitQuantity, int remainQuantity, Set<LocalDate> pickupDates){
        return new ConfirmEntryResponse(
                dropProduct.getName(), dropProduct.getDescription(),
                dropProduct.getImageUrl(), dropProduct.getPrice(), limitQuantity, remainQuantity, pickupDates
        );
    }
}
