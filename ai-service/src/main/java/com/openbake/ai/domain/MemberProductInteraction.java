package com.openbake.ai.domain;

import com.openbake.common.event.MemberInteractionEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "member_product_interactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberProductInteraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "drop_id")
    private Long dropId;

    @Enumerated(EnumType.STRING)
    @Column(name = "interaction_type", nullable = false, length = 20)
    private com.openbake.common.event.InteractionType interactionType;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "consumed_at", nullable = false)
    private Instant consumedAt;

    public static MemberProductInteraction from(MemberInteractionEvent event, Instant consumedAt) {
        MemberProductInteraction interaction = new MemberProductInteraction();
        interaction.eventId = event.eventId();
        interaction.memberId = event.memberId();
        interaction.productId = event.productId();
        interaction.dropId = event.dropId();
        interaction.interactionType = event.interactionType();
        interaction.quantity = event.quantity();
        interaction.occurredAt = event.occurredAt();
        interaction.consumedAt = consumedAt;
        return interaction;
    }
}
