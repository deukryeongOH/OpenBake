package com.openbake.product.application.event;

import com.openbake.product.domain.Product;
import java.time.Instant;
import java.util.UUID;

public record ProductChangedEvent(
        UUID eventId,
        int eventVersion,
        ProductChangedEventType eventType,
        Instant occurredAt,
        Long productId,
        String name,
        String description,
        String category,
        String type) {

    public static ProductChangedEvent changed(Product product, ProductChangedEventType eventType, Instant occurredAt) {
        return new ProductChangedEvent(
                UUID.randomUUID(),
                1,
                eventType,
                occurredAt,
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getCategory().name(),
                product.getType().name());
    }

    public static ProductChangedEvent deleted(Long productId, Instant occurredAt) {
        return new ProductChangedEvent(
                UUID.randomUUID(),
                1,
                ProductChangedEventType.DELETED,
                occurredAt,
                productId,
                null,
                null,
                null,
                null);
    }
}
