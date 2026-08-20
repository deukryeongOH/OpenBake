package com.openbake.ai.application;

import com.openbake.common.event.InteractionType;
import java.util.List;
import java.util.Map;

public record RecommendationProfile(
        Map<String, Double> categoryAffinity,
        List<Float> interestVector,
        Map<InteractionType, List<Float>> interactionVectors,
        Map<InteractionType, Double> interactionWeights,
        int validInteractionCount) {
}
