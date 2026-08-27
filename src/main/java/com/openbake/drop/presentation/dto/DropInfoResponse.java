package com.openbake.drop.presentation.dto;


import com.openbake.drop.application.dto.DropInfoResult;
import com.openbake.drop.domain.DropStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

public record DropInfoResponse(Long dropId, String name, String description, String imageUrl,
                               LocalDateTime dropStart, LocalDateTime dropEnd,
                               int limitQuantity, int price, int totalQuantity, int remainQuantity, DropStatus dropStatus,
                               Set<LocalDate> pickupDates) {
    public static DropInfoResponse of(DropInfoResult result){
        return new DropInfoResponse(result.dropId(), result.name(), result.description(), result.imageUrl(), result.dropStart(), result.dropEnd(), result.limitQuantity(),
                result.price(), result.totalQuantity(), result.remainQuantity(), result.dropStatus(), new HashSet<>(result.pickUpAvailableDates()));
    }
}
