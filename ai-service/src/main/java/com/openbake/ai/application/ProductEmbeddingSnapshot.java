package com.openbake.ai.application;

import java.util.List;

public record ProductEmbeddingSnapshot(
        Long productId,
        String category,
        String type,
        List<Float> embedding,
        double similarity) {
}
