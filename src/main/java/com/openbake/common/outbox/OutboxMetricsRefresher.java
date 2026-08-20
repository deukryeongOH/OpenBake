package com.openbake.common.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxMetricsRefresher {

    private final OutboxEventRepository repository;
    private final Clock clock = Clock.systemUTC();
    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong oldestAgeSeconds = new AtomicLong();

    public OutboxMetricsRefresher(OutboxEventRepository repository, MeterRegistry registry) {
        this.repository = repository;
        registry.gauge("openbake.outbox.pending", pending);
        registry.gauge("openbake.outbox.oldest_age_seconds", oldestAgeSeconds);
    }

    @Scheduled(
            fixedDelayString = "${openbake.ai.metrics.refresh-interval:PT30S}",
            initialDelayString = "${openbake.ai.metrics.refresh-interval:PT30S}")
    public void refresh() {
        pending.set(repository.countByStatus(OutboxStatus.PENDING));
        long age = repository.findOldestOccurredAtByStatus(OutboxStatus.PENDING)
                .map(occurredAt -> Math.max(0, Duration.between(occurredAt, clock.instant()).toSeconds()))
                .orElse(0L);
        oldestAgeSeconds.set(age);
    }
}
