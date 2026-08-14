package com.openbake.drop.application.dto;

import com.openbake.drop.domain.DropStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

public record DropInfoResult(
        LocalDateTime dropStart, LocalDateTime dropEnd,
        int limitQuantity, DropStatus dropStatus,
        String name, String description, String imageUrl,
        Set<LocalDate> pickUpAvailableDates,
        int price, int totalQuantity, int remainQuantity,
        Long sellerId, Long productId) {
    public static DropInfoResult of(
                             LocalDateTime dropStart, LocalDateTime dropEnd,
                             int limitQuantity, DropStatus dropStatus, String name, String description, String imageUrl,
                             Set<LocalDate> pickUpAvailableDates,
                             int price, int totalQuantity, int remainQuantity,
                             Long sellerId, Long productId){
        return new DropInfoResult(dropStart, dropEnd, limitQuantity, dropStatus, name,description, imageUrl,
                new HashSet<>(pickUpAvailableDates), price, totalQuantity, remainQuantity, sellerId, productId);
    }
}
