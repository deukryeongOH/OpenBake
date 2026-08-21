package com.openbake.ai.application;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class RecommendationMath {

    private static final double LN_2 = Math.log(2.0);

    private RecommendationMath() {
    }

    public static double decay(Duration age, Duration halfLife) {
        double ageDays = Math.max(0.0, age.toMillis() / 86_400_000.0);
        double halfLifeDays = halfLife.toMillis() / 86_400_000.0;
        return Math.exp(-LN_2 * ageDays / halfLifeDays);
    }

    public static double groupedScore(double baseWeight, List<Double> decays) {
        if (decays.isEmpty()) {
            return 0.0;
        }
        double averageDecay = decays.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        return baseWeight * averageDecay * Math.log1p(decays.size());
    }

    public static List<Double> minMaxNormalize(List<Double> values) {
        if (values.isEmpty()) {
            return List.of();
        }
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        if (Double.compare(min, max) == 0) {
            return values.stream().map(ignored -> 1.0).toList();
        }
        List<Double> normalized = new ArrayList<>(values.size());
        for (double value : values) {
            normalized.add((value - min) / (max - min));
        }
        return List.copyOf(normalized);
    }

    public static double cosine(List<Float> left, List<Float> right) {
        if (left == null || right == null || left.isEmpty() || left.size() != right.size()) {
            return 0.0;
        }
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int index = 0; index < left.size(); index++) {
            double l = left.get(index);
            double r = right.get(index);
            dot += l * r;
            leftNorm += l * l;
            rightNorm += r * r;
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        return dot / Math.sqrt(leftNorm * rightNorm);
    }
}
