package com.openbake.cart.presentation;

import io.swagger.v3.oas.annotations.media.Schema;
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
public class CartItemPickUpDateRequest {

    @Schema(description = "선택할 픽업 날짜. 재선택하면 덮어쓴다.", example = "2026-08-20")
    @NotNull(message = "픽업 날짜는 필수입니다.")
    private LocalDate pickUpDate;
}
