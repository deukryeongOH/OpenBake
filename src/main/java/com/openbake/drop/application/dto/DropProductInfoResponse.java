package com.openbake.drop.application.dto;

import com.openbake.drop.domain.Drop;
import com.openbake.drop.domain.DropInventory;
import com.openbake.drop.domain.DropStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

public record DropProductInfoResponse(
        String name, String description, String imageUrl,
        Set<LocalDate> pickUpAvailableDates,
        LocalDateTime dropStart, LocalDateTime dropEnd,
        int limitQuantity, int price, int totalQuantity, int remainQuantity,
        DropStatus dropStatus,
        Long dropId){

    public static DropProductInfoResponse of(Drop drop, DropInventory inventory) {
        return new DropProductInfoResponse(
                drop.getDropProduct().getName(),
                drop.getDropProduct().getDescription(),
                drop.getDropProduct().getImageUrl(),
                drop.getPickUpAvailableDate(),
                drop.getDropStart(),
                drop.getDropEnd(),
                drop.getLimitQuantity(),
                drop.getDropProduct().getPrice(),
                inventory.getTotalQuantity(),
                inventory.getRemainQuantity(),
                drop.getDropStatus(), // UPCOMING 하드코딩 대신 객체 상태 사용
                drop.getId()
        );
    }
}
