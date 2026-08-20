package com.openbake.ai.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openbake.ai.embedding")
public record EmbeddingProperties(
        String model,
        int dimensions,
        String indexName,
        String indexVersion,
        Duration connectTimeout,
        Duration readTimeout,
        Worker worker) {

    public record Worker(Duration interval, int batchSize, Duration lease) {
    }
}
