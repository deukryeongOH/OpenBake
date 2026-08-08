package com.openbake.drop.application.dto;

import com.openbake.drop.domain.Drop;
import com.openbake.drop.domain.DropInventory;
import com.openbake.drop.domain.DropStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

public record DropProductInfoResult(String name, String description, String imageUrl,
                                    Set<LocalDate> pickUpAvailableDates,
                                    LocalDateTime dropStart, LocalDateTime dropEnd,
                                    int limitQuantity, int price, int totalQuantity, int remainQuantity,
                                    DropStatus dropStatus,
                                    Long dropId) {
    public static DropProductInfoResult of(Drop savedDrop, DropInventory savedDropInventory) {
        return new DropProductInfoResult(savedDrop.getDropProduct().getName(), savedDrop.getDropProduct().getDescription(),
                savedDrop.getDropProduct().getImageUrl(), new HashSet<>(savedDrop.getPickUpAvailableDate()), savedDrop.getDropStart(),
                savedDrop.getDropEnd(), savedDrop.getLimitQuantity(), savedDrop.getDropProduct().getPrice(),
                savedDropInventory.getTotalQuantity(), savedDropInventory.getRemainQuantity(), savedDrop.getDropStatus(),
                savedDrop.getId());
    }
}
