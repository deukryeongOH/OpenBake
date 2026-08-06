package com.openbake.drop.application.dto;


import com.openbake.drop.domain.DropStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

public record DropInfoResponse(String name, String description, String imageUrl,
                               LocalDateTime dropStart, LocalDateTime dropEnd,
                               int limitQuantity, int price, int totalQuantity, int remainQuantity, DropStatus dropStatus,
                               Set<LocalDate> pickupDates) {
    public static DropInfoResponse of(String name, String description, String imageUrl,
                                      LocalDateTime dropStart, LocalDateTime dropEnd,
                                      int limitQuantity, int price, int totalQuantity, int remainQuantity, DropStatus dropStatus, Set<LocalDate> pickupDates){
        return new DropInfoResponse(name, description, imageUrl, dropStart, dropEnd, limitQuantity, price, totalQuantity, remainQuantity, dropStatus, pickupDates);
    }
}
