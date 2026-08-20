package com.openbake.common.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MemberInteractionEventTest {

    @Test
    void validatesTopicAndInteractionSpecificFields() {
        MemberInteractionEvent event = new MemberInteractionEvent(
                UUID.randomUUID(), 1, InteractionType.PURCHASE, Instant.now(),
                1L, 2L, 3L, 4, 5L);

        assertThatCode(() -> event.validateForTopic(EventTopics.ORDER_PURCHASE_CONFIRMED))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> event.validateForTopic(EventTopics.CART_ITEM_ADDED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPersonalOrInvalidContractShapeByConstructionRules() {
        MemberInteractionEvent invalidCart = new MemberInteractionEvent(
                UUID.randomUUID(), 1, InteractionType.CART_ADD, Instant.now(),
                1L, 2L, 3L, 1, null);

        assertThatThrownBy(invalidCart::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dropId");
    }
}
