package com.openbake.product.application.event;

import com.openbake.common.outbox.OutboxEvent;
import com.openbake.common.outbox.OutboxEventRepository;
import com.openbake.product.domain.Product;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class ProductChangedOutboxWriter {

    static final String TOPIC = "product.changed.v1";

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void created(Product product) {
        save(ProductChangedEvent.changed(product, ProductChangedEventType.CREATED, Instant.now()));
    }

    public void updated(Product product) {
        save(ProductChangedEvent.changed(product, ProductChangedEventType.UPDATED, Instant.now()));
    }

    public void deleted(Long productId) {
        save(ProductChangedEvent.deleted(productId, Instant.now()));
    }

    private void save(ProductChangedEvent event) {
        String payload = objectMapper.writeValueAsString(event);
        outboxEventRepository.save(OutboxEvent.create(
                event.eventId().toString(),
                TOPIC,
                event.productId().toString(),
                event.eventType().name(),
                event.eventVersion(),
                payload,
                event.occurredAt()));
    }
}
