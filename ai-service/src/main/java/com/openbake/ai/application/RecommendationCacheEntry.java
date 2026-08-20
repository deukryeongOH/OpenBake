package com.openbake.ai.application;

import com.openbake.ai.domain.RecommendationReason;
import com.openbake.ai.domain.RecommendationStrategy;
import java.util.List;

public record RecommendationCacheEntry(
        RecommendationStrategy strategy,
        List<Candidate> candidates) {

    public record Candidate(Long productId, RecommendationReason reasonCode) {
    }
}
