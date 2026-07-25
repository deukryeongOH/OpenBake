package com.openbake.drop.application.dto;

import java.time.LocalDateTime;

public record DropQuantityReservedEvent(
        Long dropId,
        Long memberId,
        int quantity,
        LocalDateTime reservedAt) {
    public static DropQuantityReservedEvent of(
            Long dropId,
            Long memberId,
            int quantity) {
        return new DropQuantityReservedEvent(
                dropId, memberId, quantity, LocalDateTime.now()
        );
    }

}
