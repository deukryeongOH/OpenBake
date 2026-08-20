package com.openbake.ai.application;

import com.openbake.ai.domain.MemberProductInteraction;
import com.openbake.common.event.InteractionType;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberBehaviorProfileCalculator {

    private final RecommendationProperties properties;

    public Optional<RecommendationProfile> calculate(
            List<MemberProductInteraction> interactions,
            Map<Long, ProductEmbeddingSnapshot> documents,
            Instant now) {
        Map<GroupKey, List<MemberProductInteraction>> groups = new HashMap<>();
        int vectorDimensions = documents.values().stream()
                .map(ProductEmbeddingSnapshot::embedding)
                .filter(this::hasVector)
                .mapToInt(List::size)
                .findFirst()
                .orElse(0);

        for (MemberProductInteraction interaction : interactions) {
            ProductEmbeddingSnapshot document = documents.get(interaction.getProductId());
            if (document == null || !hasVector(document.embedding())
                    || document.embedding().size() != vectorDimensions) {
                continue;
            }
            groups.computeIfAbsent(
                    new GroupKey(interaction.getProductId(), interaction.getInteractionType()),
                    ignored -> new ArrayList<>()).add(interaction);
        }
        if (groups.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Double> categoryScores = new HashMap<>();
        Map<InteractionType, double[]> typeVectorSums = new EnumMap<>(InteractionType.class);
        Map<InteractionType, Double> typeWeights = new EnumMap<>(InteractionType.class);
        double[] interestVector = new double[vectorDimensions];
        double totalWeight = 0.0;
        int validCount = 0;

        for (Map.Entry<GroupKey, List<MemberProductInteraction>> entry : groups.entrySet()) {
            GroupKey key = entry.getKey();
            ProductEmbeddingSnapshot document = documents.get(key.productId());
            List<Double> decays = entry.getValue().stream()
                    .map(interaction -> RecommendationMath.decay(
                            Duration.between(interaction.getOccurredAt(), now),
                            halfLife(key.interactionType())))
                    .toList();
            double groupScore = RecommendationMath.groupedScore(
                    baseWeight(key.interactionType()), decays);

            categoryScores.merge(document.category(), groupScore, Double::sum);
            addWeighted(interestVector, document.embedding(), groupScore);
            typeVectorSums.computeIfAbsent(key.interactionType(), ignored -> new double[vectorDimensions]);
            addWeighted(typeVectorSums.get(key.interactionType()), document.embedding(), groupScore);
            typeWeights.merge(key.interactionType(), groupScore, Double::sum);
            totalWeight += groupScore;
            validCount += entry.getValue().size();
        }

        if (totalWeight <= 0.0) {
            return Optional.empty();
        }
        divide(interestVector, totalWeight);

        Map<InteractionType, List<Float>> typeVectors = new EnumMap<>(InteractionType.class);
        typeVectorSums.forEach((type, vector) -> {
            divide(vector, typeWeights.get(type));
            typeVectors.put(type, toFloatList(vector));
        });

        double maxCategory = categoryScores.values().stream()
                .mapToDouble(Double::doubleValue).max().orElse(1.0);
        Map<String, Double> normalizedCategories = new HashMap<>();
        categoryScores.forEach((category, score) ->
                normalizedCategories.put(category, score / maxCategory));

        return Optional.of(new RecommendationProfile(
                Map.copyOf(normalizedCategories),
                toFloatList(interestVector),
                Map.copyOf(typeVectors),
                Map.copyOf(typeWeights),
                validCount));
    }

    private boolean hasVector(List<Float> vector) {
        return vector != null && !vector.isEmpty();
    }

    private double baseWeight(InteractionType type) {
        return switch (type) {
            case VIEW -> properties.weights().view();
            case CART_ADD -> properties.weights().cartAdd();
            case PURCHASE -> properties.weights().purchase();
        };
    }

    private Duration halfLife(InteractionType type) {
        return switch (type) {
            case VIEW -> properties.halfLife().view();
            case CART_ADD -> properties.halfLife().cartAdd();
            case PURCHASE -> properties.halfLife().purchase();
        };
    }

    private void addWeighted(double[] target, List<Float> source, double weight) {
        for (int index = 0; index < target.length; index++) {
            target[index] += source.get(index) * weight;
        }
    }

    private void divide(double[] vector, double divisor) {
        for (int index = 0; index < vector.length; index++) {
            vector[index] /= divisor;
        }
    }

    private List<Float> toFloatList(double[] vector) {
        List<Float> result = new ArrayList<>(vector.length);
        for (double value : vector) {
            result.add((float) value);
        }
        return List.copyOf(result);
    }

    private record GroupKey(Long productId, InteractionType interactionType) {
    }
}
