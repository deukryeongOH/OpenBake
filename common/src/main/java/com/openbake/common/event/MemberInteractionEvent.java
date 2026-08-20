package com.openbake.common.event;

import java.time.Instant;
import java.util.UUID;

public record MemberInteractionEvent(
        UUID eventId,
        int eventVersion,
        InteractionType interactionType,
        Instant occurredAt,
        Long memberId,
        Long productId,
        Long dropId,
        int quantity,
        Long orderId) {

    public static MemberInteractionEvent create(
            InteractionType interactionType,
            Long memberId,
            Long productId,
            Long dropId,
            int quantity,
            Long orderId,
            Instant occurredAt) {
        return new MemberInteractionEvent(
                UUID.randomUUID(), 1, interactionType, occurredAt,
                memberId, productId, dropId, quantity, orderId);
    }

    public void validate() {
        require(eventId != null, "eventId is required");
        require(eventVersion == 1, "unsupported eventVersion");
        require(interactionType != null, "interactionType is required");
        require(occurredAt != null, "occurredAt is required");
        require(memberId != null && memberId > 0, "memberId must be positive");
        require(productId != null && productId > 0, "productId must be positive");
        require(dropId == null || dropId > 0, "dropId must be positive");
        require(quantity > 0, "quantity must be positive");
        require(orderId == null || orderId > 0, "orderId must be positive");

        if (interactionType == InteractionType.VIEW) {
            require(quantity == 1, "VIEW quantity must be 1");
            require(orderId == null, "VIEW orderId must be null");
        } else if (interactionType == InteractionType.CART_ADD) {
            require(dropId == null, "CART_ADD dropId must be null");
            require(orderId == null, "CART_ADD orderId must be null");
        } else if (interactionType == InteractionType.PURCHASE) {
            require(dropId != null, "PURCHASE dropId is required");
            require(orderId != null, "PURCHASE orderId is required");
        }
    }

    public void validateForTopic(String topic) {
        validate();
        require(EventTopics.interactionTypeFor(topic) == interactionType,
                "interactionType does not match topic");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
