package com.openbake.ai.infrastructure.jpa;

import com.openbake.ai.domain.ProductEmbeddingMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductEmbeddingMetadataJpaRepository extends JpaRepository<ProductEmbeddingMetadata, Long> {
}
