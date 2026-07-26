package com.openbake.cart.presentation;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder

public class CartPickupDateRequest {
    @NotNull(message = "픽업 날짜는 필수입니다.")
    private LocalDate pickupDate;
}
