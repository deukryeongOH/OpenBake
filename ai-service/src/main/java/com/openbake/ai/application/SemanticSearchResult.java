package com.openbake.ai.application;

import java.util.List;

public record SemanticSearchResult(List<Item> items) {

    public record Item(Long productId, int rank, double score) {
    }
}
