package com.openbake.ai.domain;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record ProductChangedEvent(
        UUID eventId,
        int eventVersion,
        ProductChangeType eventType,
        Instant occurredAt,
        Long productId,
        String name,
        String description,
        String category,
        String type) {

    private static final Set<String> PRODUCT_TYPES = Set.of("GENERAL", "DROP");

    public void validate() {
        require(eventId != null, "eventId is required");
        require(eventVersion == 1, "unsupported eventVersion");
        require(eventType != null, "eventType is required");
        require(occurredAt != null, "occurredAt is required");
        require(productId != null && productId > 0, "productId must be positive");

        if (eventType != ProductChangeType.DELETED) {
            require(hasText(name), "name is required");
            require(hasText(description), "description is required");
            require(hasText(category), "category is required");
            require(PRODUCT_TYPES.contains(type), "unsupported product type");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
