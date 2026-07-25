package com.openbake.drop.application.dto;

public record PaymentCompletedEvent(
        Long dropId, Long memberId, Long orderId) {
}
