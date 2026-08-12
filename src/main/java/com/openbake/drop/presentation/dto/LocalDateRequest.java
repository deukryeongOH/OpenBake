package com.openbake.drop.presentation.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record LocalDateRequest(
        @NotNull(message = "조회할 날짜를 입력해주세요.")
        LocalDate date
) {
}