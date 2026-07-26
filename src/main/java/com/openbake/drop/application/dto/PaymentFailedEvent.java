package com.openbake.drop.application.dto;


public record PaymentFailedEvent(Long dropId, Long memberId, int quantity) {
}
