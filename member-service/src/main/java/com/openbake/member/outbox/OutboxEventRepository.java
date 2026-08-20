package com.openbake.member.outbox;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * 발행 대상 1건을 잠그고 가져온다. 여러 Pod가 동시에 publisher를 돌려도
     * SKIP LOCKED 덕분에 서로 다른 행을 집어가서 중복 claim이 안 생긴다.
     */
    @Query(
            value = """
                    SELECT * FROM outbox_events
                    WHERE status = 'PENDING'
                      AND (next_attempt_at IS NULL OR next_attempt_at <= now())
                    ORDER BY id
                    LIMIT 1
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true)
    Optional<OutboxEvent> claimNext();

    long deleteByStatusAndPublishedAtBefore(OutboxStatus status, Instant cutoff);
}
