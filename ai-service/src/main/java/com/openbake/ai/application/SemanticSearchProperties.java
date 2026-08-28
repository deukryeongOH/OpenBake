package com.openbake.ai.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("openbake.ai.semantic-search")
public record SemanticSearchProperties(
        int defaultSize,
        int maxSize,
        int maxQueryLength,
        Double minCosine) {

    public SemanticSearchProperties {
        if (defaultSize <= 0) {
            defaultSize = 50;
        }
        if (maxSize <= 0) {
            maxSize = 100;
        }
        if (maxQueryLength <= 0) {
            maxQueryLength = 200;
        }
        if (minCosine == null) {
            minCosine = 0.30;
        }
        if (minCosine < 0.0 || minCosine > 1.0) {
            throw new IllegalArgumentException("minCosine은 0 이상 1 이하여야 합니다.");
        }
    }
}
