package com.openbake.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "member_deletion_markers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberDeletionMarker {

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "latest_event_id", nullable = false, unique = true)
    private UUID latestEventId;

    @Column(name = "withdrawn_at", nullable = false)
    private Instant withdrawnAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
