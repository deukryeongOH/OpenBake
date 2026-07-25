package com.openbake.drop.application.dto;

import com.openbake.drop.domain.DropProduct;

public record ConfirmEntryResponse(
    String name, String description, String imageUrl, int price, int limitQuantity
) {

    public static ConfirmEntryResponse of(DropProduct dropProduct, int limitQuantity){
        return new ConfirmEntryResponse(
                dropProduct.getName(), dropProduct.getDescription(),
                dropProduct.getImageUrl(), dropProduct.getPrice(), limitQuantity
        );
    }
}
