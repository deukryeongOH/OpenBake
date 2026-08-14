package com.openbake.order.application.port.dto;

public record DropInfo(
        Long dropId,
        Long sellerId,
        String name,
        int price
) {
}
