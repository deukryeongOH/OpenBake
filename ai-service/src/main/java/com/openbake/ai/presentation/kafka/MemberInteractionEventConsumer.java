package com.openbake.ai.presentation.kafka;

import com.openbake.ai.application.MemberInteractionEventService;
import com.openbake.common.event.EventTopics;
import com.openbake.common.event.MemberInteractionEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class MemberInteractionEventConsumer {

    private final ObjectMapper objectMapper;
    private final MemberInteractionEventService eventService;

    @KafkaListener(topics = {
            EventTopics.PRODUCT_VIEWED,
            EventTopics.CART_ITEM_ADDED,
            EventTopics.ORDER_PURCHASE_CONFIRMED
    })
    public void consume(ConsumerRecord<String, String> record) {
        MemberInteractionEvent event = objectMapper.readValue(record.value(), MemberInteractionEvent.class);
        event.validateForTopic(record.topic());
        if (!event.memberId().toString().equals(record.key())) {
            throw new IllegalArgumentException("Kafka key must match memberId");
        }
        eventService.consume(event, record.topic(), record.partition(), record.offset());
    }
}
