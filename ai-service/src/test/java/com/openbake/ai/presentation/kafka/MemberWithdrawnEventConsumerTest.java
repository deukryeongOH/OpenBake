package com.openbake.ai.presentation.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import com.openbake.ai.application.MemberWithdrawnEventService;
import com.openbake.common.event.EventTopics;
import com.openbake.common.event.MemberWithdrawnEvent;
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
class MemberWithdrawnEventConsumerTest {

    @Mock
    private MemberWithdrawnEventService service;
    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void validatesMemberKey() {
        Instant now = Instant.now();
        MemberWithdrawnEvent event = new MemberWithdrawnEvent(
                UUID.randomUUID(), 1, now, 7L, now);
        MemberWithdrawnEventConsumer consumer = new MemberWithdrawnEventConsumer(mapper, service);

        consumer.consume(new ConsumerRecord<>(
                EventTopics.MEMBER_WITHDRAWN, 0, 3L, "7", mapper.writeValueAsString(event)));
        verify(service).consume(event, EventTopics.MEMBER_WITHDRAWN, 0, 3L);

        assertThatThrownBy(() -> consumer.consume(new ConsumerRecord<>(
                EventTopics.MEMBER_WITHDRAWN, 0, 4L, "8", mapper.writeValueAsString(event))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key");
    }
}
