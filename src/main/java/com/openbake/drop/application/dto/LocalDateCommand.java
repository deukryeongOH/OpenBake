package com.openbake.drop.application.dto;

import java.time.LocalDate;

public record LocalDateCommand(LocalDate todayDate) {
    public static LocalDateCommand of(LocalDate todayDate) {
        return new LocalDateCommand(todayDate);
    }
}