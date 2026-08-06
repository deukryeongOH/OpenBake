package com.openbake.drop.presentation.dto;

import jakarta.validation.constraints.Positive;

public record DropReserveRequest(
        @Positive(message = "수량은 1개 이상이어야 합니다.")
        int quantity
) {
}