package com.openbake.ai.presentation.kafka;

import com.openbake.ai.application.ProductChangedEventService;
import com.openbake.ai.domain.ProductChangedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class ProductChangedEventConsumer {

    private final ObjectMapper objectMapper;
    private final ProductChangedEventService eventService;

    @KafkaListener(topics = "product.changed.v1")
    public void consume(ConsumerRecord<String, String> record) {
        ProductChangedEvent event = objectMapper.readValue(record.value(), ProductChangedEvent.class);
        event.validate();
        if (!event.productId().toString().equals(record.key())) {
            throw new IllegalArgumentException("Kafka key must match productId");
        }
        eventService.consume(event, record.topic(), record.partition(), record.offset());
    }
}
