package com.openbake.product.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openbake.search")
public record SearchProperties(Semantic semantic, Rrf rrf) {

    public SearchProperties {
        if (semantic == null) {
            semantic = new Semantic(true, Duration.ofMillis(500), 2, 200, 10);
        }
        if (rrf == null) {
            rrf = new Rrf(60);
        }
    }

    public record Semantic(
            boolean enabled,
            Duration timeout,
            int poolMultiplier,
            int candidateMax,
            int maxResults) {

        public Semantic {
            if (maxResults <= 0) {
                maxResults = 10;
            }
        }
    }

    public record Rrf(int k) {
    }
}
