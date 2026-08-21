package com.openbake.ai.application;

import com.openbake.ai.application.port.EmbeddingClient;
import com.openbake.ai.application.port.RecommendationEmbeddingIndex;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SemanticSearchService {

    private final EmbeddingClient embeddingClient;
    private final RecommendationEmbeddingIndex embeddingIndex;
    private final SemanticSearchProperties properties;

    public SemanticSearchResult search(SemanticSearchRequest request) {
        String query = normalize(request.query());
        validateQuery(query);
        int size = validateSize(request.size());

        List<Float> vector;
        List<ProductEmbeddingSnapshot> nearest;
        try {
            vector = embeddingClient.embed(query);
            nearest = embeddingIndex.findGeneralNearest(vector, size, request.category());
        } catch (RuntimeException exception) {
            throw new SemanticSearchUnavailableException(exception);
        }

        List<SemanticSearchResult.Item> items = new ArrayList<>();
        int rank = 1;
        for (ProductEmbeddingSnapshot snapshot : nearest) {
            items.add(new SemanticSearchResult.Item(snapshot.productId(), rank, snapshot.similarity()));
            rank++;
        }
        return new SemanticSearchResult(List.copyOf(items));
    }

    private String normalize(String query) {
        if (query == null) {
            return "";
        }
        return query.trim().replaceAll("\\s+", " ");
    }

    private void validateQuery(String query) {
        if (query.isEmpty() || query.length() > properties.maxQueryLength()) {
            throw new IllegalArgumentException(
                    "query는 공백 제외 1~" + properties.maxQueryLength() + "자여야 합니다.");
        }
    }

    private int validateSize(Integer requestedSize) {
        int size = requestedSize == null ? properties.defaultSize() : requestedSize;
        if (size < 1 || size > properties.maxSize()) {
            throw new IllegalArgumentException("size는 1 이상 " + properties.maxSize() + " 이하여야 합니다.");
        }
        return size;
    }
}
