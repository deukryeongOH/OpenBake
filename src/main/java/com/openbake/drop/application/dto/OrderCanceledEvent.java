package com.openbake.drop.application.dto;

public record OrderCanceledEvent(Long dropId, Long memberId, int quantity) {
}
