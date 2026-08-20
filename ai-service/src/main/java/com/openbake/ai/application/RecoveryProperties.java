package com.openbake.ai.application;

import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openbake.ai")
public record RecoveryProperties(
        Backfill backfill,
        Reconcile reconcile,
        Metrics metrics,
        Dlt dlt) {

    public record Backfill(int pageSize, Duration pageDelay) {
    }

    public record Reconcile(int maxDeletions) {
    }

    public record Metrics(Duration refreshInterval) {
    }

    public record Dlt(int maxFetch, Set<String> allowedTopics) {
    }
}
