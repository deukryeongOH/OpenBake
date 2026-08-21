package com.openbake.ai.infrastructure.jpa;

import com.openbake.ai.domain.ProductEmbeddingMetadata;
import com.openbake.ai.domain.ProductEmbeddingMetadataRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProductEmbeddingMetadataRepositoryAdapter implements ProductEmbeddingMetadataRepository {

    private final ProductEmbeddingMetadataJpaRepository jpaRepository;

    @Override
    public Optional<ProductEmbeddingMetadata> findById(Long productId) {
        return jpaRepository.findById(productId);
    }

    @Override
    public ProductEmbeddingMetadata save(ProductEmbeddingMetadata metadata) {
        return jpaRepository.save(metadata);
    }

    @Override
    public void deleteById(Long productId) {
        jpaRepository.deleteById(productId);
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }
}
