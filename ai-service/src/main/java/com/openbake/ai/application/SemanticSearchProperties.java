package com.openbake.ai.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("openbake.ai.semantic-search")
public record SemanticSearchProperties(
        int defaultSize,
        int maxSize,
        int maxQueryLength) {

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
    }
}
