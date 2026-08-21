package com.openbake.ai.application;

import com.openbake.ai.domain.MemberProductInteraction;
import com.openbake.ai.domain.RecommendationReason;
import com.openbake.common.event.InteractionType;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PopularRecommendationCalculator {

    public List<RecommendationCandidate> calculate(
            List<MemberProductInteraction> signals,
            Map<Long, ProductEmbeddingSnapshot> documents,
            int requestedSize,
            int candidateCount) {
        Map<Long, Double> scores = new HashMap<>();
        for (MemberProductInteraction signal : signals) {
            ProductEmbeddingSnapshot document = documents.get(signal.getProductId());
            if (signal.getDropId() != null || document == null || !"GENERAL".equals(document.type())) {
                continue;
            }
            double contribution = signal.getInteractionType() == InteractionType.PURCHASE
                    ? signal.getQuantity()
                    : 1.0;
            scores.merge(signal.getProductId(), contribution, Double::sum);
        }

        int categoryLimit = (int) Math.ceil(requestedSize / 2.0);
        Map<String, Integer> categoryCounts = new HashMap<>();
        return scores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
                .filter(entry -> {
                    String category = documents.get(entry.getKey()).category();
                    int current = categoryCounts.getOrDefault(category, 0);
                    if (current >= categoryLimit) {
                        return false;
                    }
                    categoryCounts.put(category, current + 1);
                    return true;
                })
                .limit(candidateCount)
                .map(entry -> new RecommendationCandidate(
                        entry.getKey(), entry.getValue(), RecommendationReason.POPULAR))
                .toList();
    }
}
