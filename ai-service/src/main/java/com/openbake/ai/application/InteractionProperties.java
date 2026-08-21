package com.openbake.ai.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("openbake.ai.interaction")
public record InteractionProperties(
        Duration viewSuppression,
        Duration interactionRetention,
        Duration consumedEventRetention,
        Duration deletionMarkerRetention,
        int cleanupBatchSize,
        String recommendationCacheKeyPrefix) {
}
