package com.openbake.ai.application;

public record CoreProductSource(
        Long productId,
        String name,
        String description,
        String category,
        String type) {
}
