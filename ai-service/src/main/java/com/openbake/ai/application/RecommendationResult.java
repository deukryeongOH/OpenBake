package com.openbake.ai.application;

import com.openbake.ai.domain.RecommendationReason;
import com.openbake.ai.domain.RecommendationStrategy;
import java.util.List;

public record RecommendationResult(
        RecommendationStrategy strategy,
        List<Item> items) {

    public record Item(
            Long productId,
            String name,
            String imageUrl,
            int price,
            String category,
            int remainQuantity,
            RecommendationReason reasonCode) {
    }
}
