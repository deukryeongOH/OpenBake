package com.openbake.ai.presentation.kafka;

import com.openbake.ai.application.MemberWithdrawnEventService;
import com.openbake.common.event.EventTopics;
import com.openbake.common.event.MemberWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class MemberWithdrawnEventConsumer {

    private final ObjectMapper objectMapper;
    private final MemberWithdrawnEventService eventService;

    @KafkaListener(topics = EventTopics.MEMBER_WITHDRAWN)
    public void consume(ConsumerRecord<String, String> record) {
        MemberWithdrawnEvent event = objectMapper.readValue(record.value(), MemberWithdrawnEvent.class);
        event.validate();
        if (!event.memberId().toString().equals(record.key())) {
            throw new IllegalArgumentException("Kafka key must match memberId");
        }
        eventService.consume(event, record.topic(), record.partition(), record.offset());
    }
}
