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
        Double minCosine,
        Integer coldStartThreshold,
        Double coldStartCategoryWeight,
        Double establishedCategoryWeight,
        Weights weights,
        HalfLife halfLife) {

    public RecommendationProperties {
        if (minCosine == null) {
            minCosine = 0.30;
        }
        if (coldStartThreshold == null || coldStartThreshold <= 0) {
            coldStartThreshold = 3;
        }
        if (coldStartCategoryWeight == null) {
            coldStartCategoryWeight = 0.60;
        }
        if (establishedCategoryWeight == null) {
            establishedCategoryWeight = 0.30;
        }
        if (minCosine < 0.0 || minCosine > 1.0) {
            throw new IllegalArgumentException("minCosine은 0 이상 1 이하여야 합니다.");
        }
        if (coldStartCategoryWeight < 0.0 || coldStartCategoryWeight > 1.0
                || establishedCategoryWeight < 0.0 || establishedCategoryWeight > 1.0) {
            throw new IllegalArgumentException("카테고리 가중치는 0 이상 1 이하여야 합니다.");
        }
    }

    public record Weights(double view, double cartAdd, double purchase) {
    }

    public record HalfLife(Duration view, Duration cartAdd, Duration purchase) {
    }
}
