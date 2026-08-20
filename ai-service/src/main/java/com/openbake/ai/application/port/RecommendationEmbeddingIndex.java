package com.openbake.ai.application.port;

import com.openbake.ai.application.ProductEmbeddingSnapshot;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface RecommendationEmbeddingIndex {

    Map<Long, ProductEmbeddingSnapshot> findAll(Collection<Long> productIds);

    List<ProductEmbeddingSnapshot> findGeneralNearest(List<Float> vector, int size);
}
