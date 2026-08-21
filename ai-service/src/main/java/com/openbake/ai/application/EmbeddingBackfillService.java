package com.openbake.ai.application;

import com.openbake.ai.application.EmbeddingRecoveryScheduler.ScheduleResult;
import com.openbake.ai.application.port.CoreProductSourceClient;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmbeddingBackfillService {

    private final CoreProductSourceClient coreProductSourceClient;
    private final EmbeddingRecoveryScheduler scheduler;
    private final RecoveryProperties properties;

    public BackfillResult backfill() {
        int inspected = 0;
        int created = 0;
        int skipped = 0;
        int pageNumber = 0;
        while (true) {
            var page = coreProductSourceClient.fetchPage(pageNumber, properties.backfill().pageSize());
            for (CoreProductSource product : page.content()) {
                inspected++;
                if (scheduler.schedule(product, true) == ScheduleResult.SCHEDULED) {
                    created++;
                } else {
                    skipped++;
                }
            }
            if (page.last() || page.content().isEmpty()) {
                break;
            }
            pause(properties.backfill().pageDelay());
            pageNumber++;
        }
        return new BackfillResult(inspected, created, skipped);
    }

    private void pause(Duration delay) {
        if (delay.isZero() || delay.isNegative()) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("embedding backfill interrupted", exception);
        }
    }

    public record BackfillResult(int inspectedCount, int createdCount, int skippedCount) {
    }
}
