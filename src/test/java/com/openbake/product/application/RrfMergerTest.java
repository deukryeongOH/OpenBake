package com.openbake.product.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RrfMergerTest {

    @Test
    void includesProductsFoundOnlyInOneSide() {
        List<Long> merged = RrfMerger.merge(List.of(1L, 2L), List.of(3L, 4L), 60);

        assertThat(merged).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
    }

    @Test
    void productsAppearingInBothSidesRankAboveSingleSideMatches() {
        List<Long> merged = RrfMerger.merge(List.of(5L, 1L), List.of(5L, 2L), 60);

        assertThat(merged.getFirst()).isEqualTo(5L);
    }

    @Test
    void lowerRankInBothListsOutranksHigherCombinedRank() {
        // 1위/2위가 겹치는 상품이, 1위 하나만 있는 상품보다 위로 온다
        List<Long> merged = RrfMerger.merge(List.of(10L, 20L), List.of(20L, 10L), 60);

        assertThat(merged.get(0)).isIn(10L, 20L);
        assertThat(merged).hasSize(2);
    }

    @Test
    void rankInversionAcrossSourcesIsReflectedInScore() {
        // lexical 1위/semantic 2위(100)와 lexical 2위/semantic 1위(200)는 점수가 같아 동률이고,
        // 어느 한쪽에만 있는 300(lexical/semantic 3위 상당)은 항상 둘보다 아래로 온다
        List<Long> lexical = List.of(100L, 200L);
        List<Long> semantic = List.of(200L, 100L, 300L);

        List<Long> merged = RrfMerger.merge(lexical, semantic, 60);

        assertThat(merged).hasSize(3);
        assertThat(merged.get(2)).isEqualTo(300L);
        assertThat(merged.subList(0, 2)).containsExactlyInAnyOrder(100L, 200L);
    }

    @Test
    void deduplicatesRepeatedProductId() {
        List<Long> merged = RrfMerger.merge(List.of(1L, 1L, 2L), List.of(1L), 60);

        assertThat(merged).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void emptySemanticListPreservesLexicalOrder() {
        List<Long> lexical = List.of(3L, 1L, 2L);

        List<Long> merged = RrfMerger.merge(lexical, List.of(), 60);

        assertThat(merged).containsExactly(3L, 1L, 2L);
    }

    @Test
    void bothEmptyProducesEmptyResult() {
        assertThat(RrfMerger.merge(List.of(), List.of(), 60)).isEmpty();
    }
}
