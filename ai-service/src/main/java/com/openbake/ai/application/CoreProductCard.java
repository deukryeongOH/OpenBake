package com.openbake.ai.application;

public record CoreProductCard(
        Long productId,
        String name,
        String imageUrl,
        int price,
        String category,
        int remainQuantity) {
}
