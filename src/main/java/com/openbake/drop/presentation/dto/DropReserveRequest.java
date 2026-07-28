package com.openbake.drop.presentation.dto;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class DropReserveRequest {
    @Positive(message = "수량은 1개 이상이어야 합니다.")
    private int quantity;
}
