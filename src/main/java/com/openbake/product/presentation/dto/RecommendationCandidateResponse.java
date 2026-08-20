package com.openbake.product.presentation.dto;

import com.openbake.product.application.RecommendationProduct;
import com.openbake.product.domain.Category;
import java.util.List;

public record RecommendationCandidateResponse(List<Product> products) {

    public static RecommendationCandidateResponse from(List<RecommendationProduct> products) {
        return new RecommendationCandidateResponse(products.stream().map(Product::from).toList());
    }

    public record Product(
            Long productId,
            String name,
            String imageUrl,
            int price,
            Category category,
            int remainQuantity) {

        private static Product from(RecommendationProduct product) {
            return new Product(
                    product.productId(), product.name(), product.imageUrl(), product.price(),
                    product.category(), product.remainQuantity());
        }
    }
}
