package com.openbake.ai.presentation.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import com.openbake.ai.application.MemberInteractionEventService;
import com.openbake.common.event.EventTopics;
import com.openbake.common.event.InteractionType;
import com.openbake.common.event.MemberInteractionEvent;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class MemberInteractionEventConsumerTest {

    @Mock
    private MemberInteractionEventService service;
    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void validatesKeyAndTopicTypeBeforeDelegating() {
        MemberInteractionEvent event = new MemberInteractionEvent(
                UUID.randomUUID(), 1, InteractionType.CART_ADD, Instant.now(),
                7L, 8L, null, 2, null);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                EventTopics.CART_ITEM_ADDED, 1, 9L, "7", mapper.writeValueAsString(event));

        new MemberInteractionEventConsumer(mapper, service).consume(record);

        verify(service).consume(event, EventTopics.CART_ITEM_ADDED, 1, 9L);
    }

    @Test
    void rejectsMismatchedKeyAndTopic() {
        MemberInteractionEvent event = new MemberInteractionEvent(
                UUID.randomUUID(), 1, InteractionType.VIEW, Instant.now(),
                7L, 8L, null, 1, null);
        MemberInteractionEventConsumer consumer = new MemberInteractionEventConsumer(mapper, service);

        assertThatThrownBy(() -> consumer.consume(new ConsumerRecord<>(
                EventTopics.PRODUCT_VIEWED, 0, 1L, "99", mapper.writeValueAsString(event))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key");
        assertThatThrownBy(() -> consumer.consume(new ConsumerRecord<>(
                EventTopics.CART_ITEM_ADDED, 0, 2L, "7", mapper.writeValueAsString(event))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("interactionType");
    }
}
