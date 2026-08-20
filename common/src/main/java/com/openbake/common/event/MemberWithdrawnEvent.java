package com.openbake.common.event;

import java.time.Instant;
import java.util.UUID;

public record MemberWithdrawnEvent(
        UUID eventId,
        int eventVersion,
        Instant occurredAt,
        Long memberId,
        Instant withdrawnAt) {

    public static MemberWithdrawnEvent create(Long memberId, Instant withdrawnAt) {
        return new MemberWithdrawnEvent(UUID.randomUUID(), 1, withdrawnAt, memberId, withdrawnAt);
    }

    public void validate() {
        require(eventId != null, "eventId is required");
        require(eventVersion == 1, "unsupported eventVersion");
        require(occurredAt != null, "occurredAt is required");
        require(memberId != null && memberId > 0, "memberId must be positive");
        require(withdrawnAt != null, "withdrawnAt is required");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
