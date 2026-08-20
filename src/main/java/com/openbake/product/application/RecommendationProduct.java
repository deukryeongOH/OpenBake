package com.openbake.product.application;

import com.openbake.product.domain.Category;

public record RecommendationProduct(
        Long productId,
        String name,
        String imageUrl,
        int price,
        Category category,
        int remainQuantity) {
}
