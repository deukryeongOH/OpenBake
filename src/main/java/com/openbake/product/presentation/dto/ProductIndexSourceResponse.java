package com.openbake.product.presentation.dto;

import com.openbake.product.domain.Product;

public record ProductIndexSourceResponse(
        Long productId,
        String name,
        String description,
        String category,
        String type) {

    public static ProductIndexSourceResponse from(Product product) {
        return new ProductIndexSourceResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getCategory().name(),
                product.getType().name());
    }
}
