package com.openbake.interaction.application;

import java.time.Instant;

public record ProductViewedEvent(Long memberId, Long productId, Long dropId, Instant occurredAt) {
}
