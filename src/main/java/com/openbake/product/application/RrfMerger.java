package com.openbake.product.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** BM25 순위와 의미 검색 순위를 RRF(Reciprocal Rank Fusion)로 병합하는 순수 함수 모음이다. */
public final class RrfMerger {

    private RrfMerger() {
    }

    public static List<Long> merge(List<Long> lexicalRanked, List<Long> semanticRanked, int k) {
        Map<Long, Double> scores = new LinkedHashMap<>();
        accumulate(scores, lexicalRanked, k);
        accumulate(scores, semanticRanked, k);
        return scores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();
    }

    private static void accumulate(Map<Long, Double> scores, List<Long> ranked, int k) {
        Map<Long, Integer> ranks = rank(ranked);
        ranks.forEach((productId, rank) -> scores.merge(productId, 1.0 / (k + rank), Double::sum));
    }

    private static Map<Long, Integer> rank(List<Long> ids) {
        Map<Long, Integer> ranks = new LinkedHashMap<>();
        int rank = 1;
        for (Long id : ids) {
            ranks.putIfAbsent(id, rank);
            rank++;
        }
        return ranks;
    }
}
