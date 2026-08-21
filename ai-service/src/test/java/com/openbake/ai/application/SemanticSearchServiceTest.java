package com.openbake.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.openbake.ai.application.port.EmbeddingClient;
import com.openbake.ai.application.port.RecommendationEmbeddingIndex;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SemanticSearchServiceTest {

    @Mock
    private EmbeddingClient embeddingClient;
    @Mock
    private RecommendationEmbeddingIndex embeddingIndex;

    private SemanticSearchService service;

    @BeforeEach
    void setUp() {
        SemanticSearchProperties properties = new SemanticSearchProperties(50, 100, 200);
        service = new SemanticSearchService(embeddingClient, embeddingIndex, properties);
    }

    @Test
    void normalizesLeadingTrailingAndRepeatedWhitespace() {
        given(embeddingClient.embed("초코 크루아상")).willReturn(List.of(0.1f));
        given(embeddingIndex.findGeneralNearest(any(), anyInt(), any())).willReturn(List.of());

        service.search(new SemanticSearchRequest("  초코   크루아상  ", null, null));

        verify(embeddingClient).embed("초코 크루아상");
    }

    @Test
    void blankQueryAfterTrimIsRejected() {
        assertThatThrownBy(() -> service.search(new SemanticSearchRequest("   ", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(embeddingClient, never()).embed(any());
    }

    @Test
    void queryAtMaxLengthIsAccepted() {
        String query = "a".repeat(200);
        given(embeddingClient.embed(query)).willReturn(List.of(0.1f));
        given(embeddingIndex.findGeneralNearest(any(), anyInt(), any())).willReturn(List.of());

        service.search(new SemanticSearchRequest(query, null, null));

        verify(embeddingClient).embed(query);
    }

    @Test
    void queryOverMaxLengthIsRejected() {
        String query = "a".repeat(201);

        assertThatThrownBy(() -> service.search(new SemanticSearchRequest(query, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sizeAtBoundsIsAccepted() {
        given(embeddingClient.embed(any())).willReturn(List.of(0.1f));
        given(embeddingIndex.findGeneralNearest(any(), eq(1), any())).willReturn(List.of());
        given(embeddingIndex.findGeneralNearest(any(), eq(100), any())).willReturn(List.of());

        service.search(new SemanticSearchRequest("query", 1, null));
        service.search(new SemanticSearchRequest("query", 100, null));
    }

    @Test
    void sizeZeroIsRejected() {
        assertThatThrownBy(() -> service.search(new SemanticSearchRequest("query", 0, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sizeOverMaxIsRejected() {
        assertThatThrownBy(() -> service.search(new SemanticSearchRequest("query", 101, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defaultSizeIsUsedWhenNotProvided() {
        given(embeddingClient.embed(any())).willReturn(List.of(0.1f));
        given(embeddingIndex.findGeneralNearest(any(), eq(50), any())).willReturn(List.of());

        service.search(new SemanticSearchRequest("query", null, null));

        verify(embeddingIndex).findGeneralNearest(any(), eq(50), any());
    }

    @Test
    void rankStartsAtOneAndFollowsIndexOrder() {
        given(embeddingClient.embed(any())).willReturn(List.of(0.1f));
        given(embeddingIndex.findGeneralNearest(any(), anyInt(), any())).willReturn(List.of(
                new ProductEmbeddingSnapshot(10L, "MEAL_BREADS", "GENERAL", List.of(0.1f), 0.9),
                new ProductEmbeddingSnapshot(20L, "MEAL_BREADS", "GENERAL", List.of(0.1f), 0.5)));

        SemanticSearchResult result = service.search(new SemanticSearchRequest("query", null, null));

        assertThat(result.items()).extracting(SemanticSearchResult.Item::rank).containsExactly(1, 2);
        assertThat(result.items()).extracting(SemanticSearchResult.Item::productId).containsExactly(10L, 20L);
    }

    @Test
    void embeddingFailureBecomesUnavailableException() {
        given(embeddingClient.embed(any())).willThrow(new RuntimeException("openai down"));

        assertThatThrownBy(() -> service.search(new SemanticSearchRequest("query", null, null)))
                .isInstanceOf(SemanticSearchUnavailableException.class);
    }

    @Test
    void elasticsearchFailureBecomesUnavailableException() {
        given(embeddingClient.embed(any())).willReturn(List.of(0.1f));
        given(embeddingIndex.findGeneralNearest(any(), anyInt(), any()))
                .willThrow(new RuntimeException("es down"));

        assertThatThrownBy(() -> service.search(new SemanticSearchRequest("query", null, null)))
                .isInstanceOf(SemanticSearchUnavailableException.class);
    }

    @Test
    void categoryIsPassedThroughToIndex() {
        given(embeddingClient.embed(any())).willReturn(List.of(0.1f));
        given(embeddingIndex.findGeneralNearest(any(), anyInt(), eq("MEAL_BREADS"))).willReturn(List.of());

        service.search(new SemanticSearchRequest("query", null, "MEAL_BREADS"));

        verify(embeddingIndex).findGeneralNearest(any(), anyInt(), eq("MEAL_BREADS"));
    }
}
