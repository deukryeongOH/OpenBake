package com.openbake.ai.domain;

import java.util.Optional;

public interface ProductEmbeddingMetadataRepository {

    Optional<ProductEmbeddingMetadata> findById(Long productId);

    ProductEmbeddingMetadata save(ProductEmbeddingMetadata metadata);

    void deleteById(Long productId);

    long count();
}
