package com.openbake.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.openbake.ai.application.RecommendationCacheEntry.Candidate;
import com.openbake.ai.application.port.CoreRecommendationClient;
import com.openbake.ai.application.port.RecommendationCache;
import com.openbake.ai.application.port.RecommendationEmbeddingIndex;
import com.openbake.ai.domain.MemberProductInteraction;
import com.openbake.ai.domain.RecommendationReason;
import com.openbake.ai.domain.RecommendationStrategy;
import com.openbake.ai.infrastructure.jpa.MemberProductInteractionJpaRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private MemberProductInteractionJpaRepository interactionRepository;
    @Mock
    private RecommendationEmbeddingIndex embeddingIndex;
    @Mock
    private MemberBehaviorProfileCalculator profileCalculator;
    @Mock
    private PersonalizedRecommendationScorer scorer;
    @Mock
    private PopularRecommendationCalculator popularCalculator;
    @Mock
    private CoreRecommendationClient coreClient;
    @Mock
    private RecommendationCache cache;

    private RecommendationService service;

    @BeforeEach
    void setUp() {
        service = new RecommendationService(
                interactionRepository, embeddingIndex, profileCalculator, scorer,
                popularCalculator, coreClient, cache, properties(),
                Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneOffset.UTC));
        given(cache.find(any())).willReturn(Optional.empty());
    }

    @Test
    void fillsPersonalizedShortfallWithUniquePopularCandidatesAtTheEnd() {
        Long memberId = 7L;
        MemberProductInteraction interaction = mock(MemberProductInteraction.class);
        given(interaction.getProductId()).willReturn(100L);
        given(interactionRepository
                .findAllByMemberIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(
                        eq(memberId), any()))
                .willReturn(List.of(interaction));
        RecommendationProfile profile = new RecommendationProfile(
                Map.of(), List.of(1.0f), Map.of(), Map.of(), 1);
        given(embeddingIndex.findAll(List.of(100L))).willReturn(Map.of());
        given(profileCalculator.calculate(eq(List.of(interaction)), eq(Map.of()), any()))
                .willReturn(Optional.of(profile));
        given(embeddingIndex.findGeneralNearest(eq(profile.interestVector()), any(Integer.class)))
                .willReturn(List.of());
        given(scorer.score(eq(profile), eq(List.of()))).willReturn(List.of(
                new RecommendationCandidate(1L, 1.0, RecommendationReason.PREFERRED_CATEGORY),
                new RecommendationCandidate(2L, 0.9, RecommendationReason.SIMILAR_TO_VIEWED)));
        given(coreClient.validate(memberId, List.of(1L, 2L)))
                .willReturn(List.of(card(1L), card(2L)));

        MemberProductInteraction popularSignal = mock(MemberProductInteraction.class);
        given(popularSignal.getProductId()).willReturn(2L);
        given(interactionRepository.findPopularitySignals(any(), any()))
                .willReturn(List.of(popularSignal));
        given(embeddingIndex.findAll(List.of(2L))).willReturn(Map.of());
        given(popularCalculator.calculate(any(), any(), eq(4), any(Integer.class)))
                .willReturn(List.of(
                        new RecommendationCandidate(2L, 10.0, RecommendationReason.POPULAR),
                        new RecommendationCandidate(3L, 9.0, RecommendationReason.POPULAR),
                        new RecommendationCandidate(4L, 8.0, RecommendationReason.POPULAR)));
        given(coreClient.validate(memberId, List.of(2L, 3L, 4L)))
                .willReturn(List.of(card(2L), card(3L), card(4L)));

        RecommendationResult result = service.recommend(memberId, 4);

        assertThat(result.strategy()).isEqualTo(RecommendationStrategy.PERSONALIZED);
        assertThat(result.items()).extracting(RecommendationResult.Item::productId)
                .containsExactly(1L, 2L, 3L, 4L);
        assertThat(result.items()).extracting(RecommendationResult.Item::reasonCode)
                .containsExactly(
                        RecommendationReason.PREFERRED_CATEGORY,
                        RecommendationReason.SIMILAR_TO_VIEWED,
                        RecommendationReason.POPULAR,
                        RecommendationReason.POPULAR);

        ArgumentCaptor<RecommendationCacheEntry> cacheEntry =
                ArgumentCaptor.forClass(RecommendationCacheEntry.class);
        verify(cache).save(eq(memberId), cacheEntry.capture());
        assertThat(cacheEntry.getValue().candidates()).extracting(Candidate::productId)
                .containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    void returnsShortPersonalizedListWhenPopularCandidatesCannotFillIt() {
        Long memberId = 8L;
        MemberProductInteraction interaction = mock(MemberProductInteraction.class);
        given(interaction.getProductId()).willReturn(100L);
        given(interactionRepository
                .findAllByMemberIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(
                        eq(memberId), any()))
                .willReturn(List.of(interaction));
        RecommendationProfile profile = new RecommendationProfile(
                Map.of(), List.of(1.0f), Map.of(), Map.of(), 1);
        given(embeddingIndex.findAll(List.of(100L))).willReturn(Map.of());
        given(profileCalculator.calculate(any(), any(), any())).willReturn(Optional.of(profile));
        given(embeddingIndex.findGeneralNearest(any(), any(Integer.class))).willReturn(List.of());
        given(scorer.score(any(), any())).willReturn(List.of(
                new RecommendationCandidate(1L, 1.0, RecommendationReason.PREFERRED_CATEGORY)));
        given(coreClient.validate(memberId, List.of(1L))).willReturn(List.of(card(1L)));
        given(interactionRepository.findPopularitySignals(any(), any())).willReturn(List.of());

        RecommendationResult result = service.recommend(memberId, 4);

        assertThat(result.items()).extracting(RecommendationResult.Item::productId).containsExactly(1L);
    }

    @Test
    void emptyScorerResultWithPopularSignalsFallsBackToPopularStrategy() {
        Long memberId = 9L;
        stubProfileWithScorerResult(memberId, List.of());

        MemberProductInteraction popularSignal = mock(MemberProductInteraction.class);
        given(popularSignal.getProductId()).willReturn(3L);
        given(interactionRepository.findPopularitySignals(any(), any()))
                .willReturn(List.of(popularSignal));
        given(embeddingIndex.findAll(List.of(3L))).willReturn(Map.of());
        given(popularCalculator.calculate(any(), any(), eq(4), any(Integer.class)))
                .willReturn(List.of(
                        new RecommendationCandidate(3L, 10.0, RecommendationReason.POPULAR)));
        given(coreClient.validate(memberId, List.of(3L))).willReturn(List.of(card(3L)));

        RecommendationResult result = service.recommend(memberId, 4);

        assertThat(result.strategy()).isEqualTo(RecommendationStrategy.POPULAR);
        assertThat(result.items()).extracting(RecommendationResult.Item::productId).containsExactly(3L);
        assertThat(result.items()).extracting(RecommendationResult.Item::reasonCode)
                .containsExactly(RecommendationReason.POPULAR);
    }

    @Test
    void emptyScorerResultWithoutPopularSignalsFallsBackToLatestStrategy() {
        Long memberId = 10L;
        stubProfileWithScorerResult(memberId, List.of());
        given(interactionRepository.findPopularitySignals(any(), any())).willReturn(List.of());
        given(coreClient.latest(memberId, 20)).willReturn(List.of(card(99L)));

        RecommendationResult result = service.recommend(memberId, 4);

        assertThat(result.strategy()).isEqualTo(RecommendationStrategy.LATEST);
        assertThat(result.items()).extracting(RecommendationResult.Item::productId).containsExactly(99L);
        assertThat(result.items()).extracting(RecommendationResult.Item::reasonCode)
                .containsExactly(RecommendationReason.LATEST);
    }

    private void stubProfileWithScorerResult(
            Long memberId,
            List<RecommendationCandidate> scorerResult) {
        MemberProductInteraction interaction = mock(MemberProductInteraction.class);
        given(interaction.getProductId()).willReturn(100L);
        given(interactionRepository
                .findAllByMemberIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(
                        eq(memberId), any()))
                .willReturn(List.of(interaction));
        RecommendationProfile profile = new RecommendationProfile(
                Map.of(), List.of(1.0f), Map.of(), Map.of(), 1);
        given(embeddingIndex.findAll(List.of(100L))).willReturn(Map.of());
        given(profileCalculator.calculate(eq(List.of(interaction)), eq(Map.of()), any()))
                .willReturn(Optional.of(profile));
        given(embeddingIndex.findGeneralNearest(eq(profile.interestVector()), any(Integer.class)))
                .willReturn(List.of());
        given(scorer.score(eq(profile), eq(List.of()))).willReturn(scorerResult);
    }

    private static CoreProductCard card(Long id) {
        return new CoreProductCard(
                id, "product-" + id, "https://example.test/" + id,
                1_000, "MEAL_BREADS", 10);
    }

    private static RecommendationProperties properties() {
        return new RecommendationProperties(
                Duration.ofMinutes(15), 10, 20, 5, 100,
                Duration.ofDays(90), Duration.ofDays(30), Duration.ofDays(7),
                Duration.ofSeconds(3), 0.30, 3, 0.60, 0.30,
                new RecommendationProperties.Weights(1, 3, 5),
                new RecommendationProperties.HalfLife(
                        Duration.ofDays(5), Duration.ofDays(14), Duration.ofDays(45)));
    }
}
