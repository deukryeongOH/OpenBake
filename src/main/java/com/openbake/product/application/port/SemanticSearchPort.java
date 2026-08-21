package com.openbake.product.application.port;

import com.openbake.product.domain.Category;
import java.util.List;

public interface SemanticSearchPort {

    List<SemanticCandidate> findNearest(String keyword, Category category, int size);

    record SemanticCandidate(Long productId, int rank, double score) {
    }
}
