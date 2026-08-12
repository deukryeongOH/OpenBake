package com.openbake.cart.application.port.dto;

import java.time.LocalDate;
import java.util.Set;

/**
 * cart 가 드롭에서 필요로 하는 값만 담은 DTO.
 * 가격은 drop 이 int 로 관리하므로 그대로 받고, 변환은 cart 가 한다.
 */
public record DropInfo(
        Long dropId,
        Long sellerId,
        String name,
        int price,
        String imageUrl,
        Set<LocalDate> pickupDates
) {
}
