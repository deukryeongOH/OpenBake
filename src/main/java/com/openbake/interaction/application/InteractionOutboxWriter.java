package com.openbake.interaction.application;

import com.openbake.common.event.EventTopics;
import com.openbake.common.event.InteractionType;
import com.openbake.common.event.MemberInteractionEvent;
import com.openbake.common.outbox.OutboxEvent;
import com.openbake.common.outbox.OutboxEventRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class InteractionOutboxWriter {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void cartAdded(Long memberId, Long productId, int requestedQuantity) {
        save(EventTopics.CART_ITEM_ADDED, MemberInteractionEvent.create(
                InteractionType.CART_ADD, memberId, productId, null,
                requestedQuantity, null, Instant.now()));
    }

    public void purchaseConfirmed(
            Long memberId, Long productId, Long dropId, int quantity, Long orderId, Instant occurredAt) {
        save(EventTopics.ORDER_PURCHASE_CONFIRMED, MemberInteractionEvent.create(
                InteractionType.PURCHASE, memberId, productId, dropId,
                quantity, orderId, occurredAt));
    }

    private void save(String topic, MemberInteractionEvent event) {
        event.validateForTopic(topic);
        String payload = objectMapper.writeValueAsString(event);
        outboxEventRepository.save(OutboxEvent.create(
                event.eventId().toString(), topic, event.memberId().toString(),
                event.interactionType().name(), event.eventVersion(), payload, event.occurredAt()));
    }
}
