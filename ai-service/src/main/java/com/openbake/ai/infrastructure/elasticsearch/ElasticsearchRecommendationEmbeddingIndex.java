package com.openbake.ai.infrastructure.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.openbake.ai.application.EmbeddingProperties;
import com.openbake.ai.application.ProductEmbeddingSnapshot;
import com.openbake.ai.application.port.RecommendationEmbeddingIndex;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ElasticsearchRecommendationEmbeddingIndex implements RecommendationEmbeddingIndex {

    private final ElasticsearchClient client;
    private final EmbeddingProperties properties;

    @Override
    public Map<Long, ProductEmbeddingSnapshot> findAll(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        try {
            List<String> ids = productIds.stream().distinct().map(String::valueOf).toList();
            var response = client.mget(
                    request -> request.index(properties.indexName()).ids(ids),
                    ProductEmbeddingDocument.class);
            Map<Long, ProductEmbeddingSnapshot> documents = new LinkedHashMap<>();
            response.docs().forEach(item -> {
                if (item.isResult() && item.result().found() && item.result().source() != null) {
                    ProductEmbeddingDocument source = item.result().source();
                    Long productId = source.getProductId() == null
                            ? Long.valueOf(item.result().id())
                            : source.getProductId();
                    documents.put(productId, snapshot(productId, source, 0.0));
                }
            });
            return Map.copyOf(documents);
        } catch (IOException | RuntimeException exception) {
            throw new RecommendationSearchException("Elasticsearch mget failed", exception);
        }
    }

    @Override
    public List<ProductEmbeddingSnapshot> findGeneralNearest(List<Float> vector, int size) {
        try {
            int numCandidates = Math.max(size, Math.min(1_000, size * 2));
            var response = client.search(request -> request
                            .index(properties.indexName())
                            .size(size)
                            .knn(knn -> knn
                                    .field("embedding")
                                    .queryVector(vector)
                                    .k(size)
                                    .numCandidates(numCandidates)
                                    .filter(query -> query.term(term -> term
                                            .field("type")
                                            .value("GENERAL")))),
                    ProductEmbeddingDocument.class);
            return response.hits().hits().stream()
                    .filter(hit -> hit.source() != null)
                    .map(hit -> snapshot(
                            hit.source().getProductId() == null
                                    ? Long.valueOf(hit.id())
                                    : hit.source().getProductId(),
                            hit.source(),
                            hit.score() == null ? 0.0 : hit.score()))
                    .toList();
        } catch (IOException | RuntimeException exception) {
            throw new RecommendationSearchException("Elasticsearch kNN failed", exception);
        }
    }

    private ProductEmbeddingSnapshot snapshot(
            Long productId, ProductEmbeddingDocument source, double similarity) {
        return new ProductEmbeddingSnapshot(
                productId,
                source.getCategory(),
                source.getType(),
                source.getEmbedding(),
                similarity);
    }

    public static final class RecommendationSearchException extends RuntimeException {
        public RecommendationSearchException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
