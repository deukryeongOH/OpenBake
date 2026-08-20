package com.openbake.ai.application;

import com.openbake.ai.domain.RecommendationReason;

public record RecommendationCandidate(
        Long productId,
        double score,
        RecommendationReason reasonCode) {
}
