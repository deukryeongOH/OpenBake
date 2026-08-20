package com.openbake.ai.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("openbake.ai.recommendation")
public record RecommendationProperties(
        Duration cacheTtl,
        int defaultSize,
        int maxSize,
        int candidateMultiplier,
        int candidateMax,
        Duration profileWindow,
        Duration popularPurchaseWindow,
        Duration popularCartWindow,
        Duration coreTimeout,
        Weights weights,
        HalfLife halfLife) {

    public record Weights(double view, double cartAdd, double purchase) {
    }

    public record HalfLife(Duration view, Duration cartAdd, Duration purchase) {
    }
}
