package com.openbake.drop.presentation.dto;

import com.openbake.drop.application.dto.ConfirmEntryResult;

import java.time.LocalDate;
import java.util.Set;

public record ConfirmEntryResponse(
    String name, String description, String imageUrl, int price, int limitQuantity, int remainQuantity, Set<LocalDate> pickupDates
) {
    public static ConfirmEntryResponse of(ConfirmEntryResult result){
        return new ConfirmEntryResponse(
                result.name(), result.description(), result.imageUrl(),
                result.price(), result.limitQuantity(), result.remainQuantity(), result.pickupDates()
        );
    }
}