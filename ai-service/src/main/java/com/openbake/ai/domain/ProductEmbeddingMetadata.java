package com.openbake.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_embedding_metadata")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductEmbeddingMetadata {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "source_hash", nullable = false, length = 100)
    private String sourceHash;

    @Column(name = "embedding_model", nullable = false, length = 100)
    private String embeddingModel;

    @Column(nullable = false)
    private int dimensions;

    @Column(name = "index_version", nullable = false, length = 20)
    private String indexVersion;

    @Column(name = "source_occurred_at", nullable = false)
    private Instant sourceOccurredAt;

    @Column(name = "embedded_at", nullable = false)
    private Instant embeddedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static ProductEmbeddingMetadata create(
            Long productId,
            String sourceHash,
            String embeddingModel,
            int dimensions,
            String indexVersion,
            Instant sourceOccurredAt,
            Instant embeddedAt) {
        ProductEmbeddingMetadata metadata = new ProductEmbeddingMetadata();
        metadata.productId = productId;
        metadata.update(sourceHash, embeddingModel, dimensions, indexVersion, sourceOccurredAt, embeddedAt);
        return metadata;
    }

    public void update(
            String sourceHash,
            String embeddingModel,
            int dimensions,
            String indexVersion,
            Instant sourceOccurredAt,
            Instant embeddedAt) {
        this.sourceHash = sourceHash;
        this.embeddingModel = embeddingModel;
        this.dimensions = dimensions;
        this.indexVersion = indexVersion;
        this.sourceOccurredAt = sourceOccurredAt;
        this.embeddedAt = embeddedAt;
        this.updatedAt = embeddedAt;
    }

    public void touch(Instant sourceOccurredAt, Instant now) {
        this.sourceOccurredAt = sourceOccurredAt;
        this.updatedAt = now;
    }

    public boolean matches(String sourceHash, String model, int dimensions, String indexVersion) {
        return this.sourceHash.equals(sourceHash)
                && this.embeddingModel.equals(model)
                && this.dimensions == dimensions
                && this.indexVersion.equals(indexVersion);
    }
}
