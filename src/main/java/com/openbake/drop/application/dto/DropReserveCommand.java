package com.openbake.drop.application.dto;

import jakarta.validation.constraints.Positive;

public record DropReserveCommand(
        int quantity
) {
    public static DropReserveCommand create(@Positive(message = "수량은 1개 이상이어야 합니다.") int quantity) {
        return new DropReserveCommand(quantity);
    }
}