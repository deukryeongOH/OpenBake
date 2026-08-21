package com.openbake.member.outbox;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** PUBLISHED된 Outbox 레코드를 7일 뒤 hard delete한다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxCleanupScheduler {

    private static final Duration RETENTION = Duration.ofDays(7);

    private final OutboxEventRepository outboxEventRepository;

    @Scheduled(cron = "${openbake.outbox.cleanup-cron:0 0 4 * * *}")
    @Transactional
    public void run() {
        Instant cutoff = Instant.now().minus(RETENTION);
        long deleted = outboxEventRepository.deleteByStatusAndPublishedAtBefore(OutboxStatus.PUBLISHED, cutoff);
        if (deleted > 0) {
            log.info("[Outbox] PUBLISHED {}일 경과 레코드 {}건 삭제", RETENTION.toDays(), deleted);
        }
    }
}
