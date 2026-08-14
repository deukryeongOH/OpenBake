package com.openbake.order.application.port.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CartInfo(
        Long memberId,
        Long dropId,
        int quantity,
        LocalDate pickupDate,
        LocalDateTime expiresAt
) {
    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }
}
