package com.openbake.interaction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.openbake.common.event.InteractionType;
import com.openbake.common.event.MemberInteractionEvent;
import com.openbake.common.outbox.OutboxEvent;
import com.openbake.common.outbox.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class InteractionOutboxWriterTest {

    @Mock
    private OutboxEventRepository repository;

    @Test
    void cartAddedKeepsPayloadEventIdAndRequestedQuantity() {
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        InteractionOutboxWriter writer = new InteractionOutboxWriter(repository, objectMapper);

        writer.cartAdded(11L, 22L, 3);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        OutboxEvent outbox = captor.getValue();
        MemberInteractionEvent payload = objectMapper.readValue(
                outbox.getPayload(), MemberInteractionEvent.class);
        assertThat(outbox.getEventId()).isEqualTo(payload.eventId().toString());
        assertThat(outbox.getTopic()).isEqualTo("cart.item-added.v1");
        assertThat(outbox.getEventKey()).isEqualTo("11");
        assertThat(payload.interactionType()).isEqualTo(InteractionType.CART_ADD);
        assertThat(payload.quantity()).isEqualTo(3);
        assertThat(payload.dropId()).isNull();
        assertThat(payload.orderId()).isNull();
    }
}
