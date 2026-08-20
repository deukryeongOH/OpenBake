package com.openbake.ai.infrastructure.elasticsearch;

import com.openbake.ai.application.port.ProductEmbeddingIndex.ProductEmbeddingIndexDocument;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductEmbeddingDocument {

    @Id
    private Long productId;
    private String name;
    private String description;
    private String category;
    private String type;
    private List<Float> embedding;
    private String sourceHash;
    private String embeddingModel;
    private String indexVersion;
    private Instant sourceOccurredAt;
    private Instant embeddedAt;

    public static ProductEmbeddingDocument from(ProductEmbeddingIndexDocument source) {
        ProductEmbeddingDocument document = new ProductEmbeddingDocument();
        document.productId = source.productId();
        document.name = source.name();
        document.description = source.description();
        document.category = source.category();
        document.type = source.type();
        document.embedding = source.embedding();
        document.sourceHash = source.sourceHash();
        document.embeddingModel = source.embeddingModel();
        document.indexVersion = source.indexVersion();
        document.sourceOccurredAt = source.sourceOccurredAt();
        document.embeddedAt = source.embeddedAt();
        return document;
    }

    public void touch(Instant sourceOccurredAt) {
        this.sourceOccurredAt = sourceOccurredAt;
    }
}
