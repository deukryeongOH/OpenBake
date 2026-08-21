package com.openbake.ai.infrastructure.jpa;

import com.openbake.ai.domain.MemberDeletionMarker;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberDeletionMarkerJpaRepository
        extends JpaRepository<MemberDeletionMarker, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO member_deletion_markers
                (member_id, latest_event_id, withdrawn_at, expires_at, created_at, updated_at)
            VALUES (:memberId, :eventId, :withdrawnAt, :expiresAt, now(), now())
            ON CONFLICT (member_id) DO UPDATE SET
                latest_event_id = EXCLUDED.latest_event_id,
                withdrawn_at = EXCLUDED.withdrawn_at,
                expires_at = EXCLUDED.expires_at,
                updated_at = now()
            WHERE EXCLUDED.withdrawn_at >= member_deletion_markers.withdrawn_at
            """, nativeQuery = true)
    int upsertLatest(
            @Param("memberId") Long memberId,
            @Param("eventId") UUID eventId,
            @Param("withdrawnAt") Instant withdrawnAt,
            @Param("expiresAt") Instant expiresAt);

    @Modifying
    @Query(value = """
            DELETE FROM member_deletion_markers
            WHERE member_id IN (
                SELECT member_id FROM member_deletion_markers
                WHERE expires_at < :now
                ORDER BY expires_at
                LIMIT :batchSize
            )
            """, nativeQuery = true)
    int deleteExpiredBatch(@Param("now") Instant now, @Param("batchSize") int batchSize);
}
