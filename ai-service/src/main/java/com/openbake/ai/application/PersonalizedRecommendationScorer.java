package com.openbake.ai.application;

import com.openbake.ai.domain.RecommendationReason;
import com.openbake.common.event.InteractionType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PersonalizedRecommendationScorer {

    public List<RecommendationCandidate> score(
            RecommendationProfile profile,
            List<ProductEmbeddingSnapshot> candidates) {
        List<Double> normalizedSimilarities = RecommendationMath.minMaxNormalize(
                candidates.stream().map(ProductEmbeddingSnapshot::similarity).toList());
        double categoryWeight = profile.validInteractionCount() < 3 ? 0.60 : 0.30;
        double vectorWeight = 1.0 - categoryWeight;
        List<RecommendationCandidate> scored = new ArrayList<>(candidates.size());

        for (int index = 0; index < candidates.size(); index++) {
            ProductEmbeddingSnapshot candidate = candidates.get(index);
            double categoryContribution = categoryWeight
                    * profile.categoryAffinity().getOrDefault(candidate.category(), 0.0);
            double vectorContribution = vectorWeight * normalizedSimilarities.get(index);
            RecommendationReason reason = categoryContribution > vectorContribution
                    ? RecommendationReason.PREFERRED_CATEGORY
                    : vectorReason(profile, candidate.embedding());
            scored.add(new RecommendationCandidate(
                    candidate.productId(), categoryContribution + vectorContribution, reason));
        }

        return scored.stream()
                .sorted(Comparator.comparingDouble(RecommendationCandidate::score).reversed()
                        .thenComparing(RecommendationCandidate::productId, Comparator.reverseOrder()))
                .toList();
    }

    private RecommendationReason vectorReason(
            RecommendationProfile profile, List<Float> candidateVector) {
        InteractionType strongest = profile.interactionVectors().entrySet().stream()
                .max(Comparator.comparingDouble(entry -> typeContribution(
                        entry.getKey(), entry.getValue(), profile.interactionWeights(), candidateVector)))
                .map(Map.Entry::getKey)
                .orElse(InteractionType.VIEW);
        return switch (strongest) {
            case VIEW -> RecommendationReason.SIMILAR_TO_VIEWED;
            case CART_ADD -> RecommendationReason.SIMILAR_TO_CART;
            case PURCHASE -> RecommendationReason.SIMILAR_TO_PURCHASED;
        };
    }

    private double typeContribution(
            InteractionType type,
            List<Float> interactionVector,
            Map<InteractionType, Double> weights,
            List<Float> candidateVector) {
        return Math.max(0.0, RecommendationMath.cosine(interactionVector, candidateVector))
                * weights.getOrDefault(type, 0.0);
    }
}
