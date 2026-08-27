package com.openbake.ai.application;

import com.openbake.ai.application.RecommendationCacheEntry.Candidate;
import com.openbake.ai.application.port.CoreRecommendationClient;
import com.openbake.ai.application.port.RecommendationCache;
import com.openbake.ai.application.port.RecommendationEmbeddingIndex;
import com.openbake.ai.domain.MemberProductInteraction;
import com.openbake.ai.domain.RecommendationReason;
import com.openbake.ai.domain.RecommendationStrategy;
import com.openbake.ai.infrastructure.jpa.MemberProductInteractionJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

    private final MemberProductInteractionJpaRepository interactionRepository;
    private final RecommendationEmbeddingIndex embeddingIndex;
    private final MemberBehaviorProfileCalculator profileCalculator;
    private final PersonalizedRecommendationScorer scorer;
    private final PopularRecommendationCalculator popularCalculator;
    private final CoreRecommendationClient coreClient;
    private final RecommendationCache cache;
    private final RecommendationProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public RecommendationResult recommend(Long memberId, Integer requestedSize) {
        int size = validateSize(requestedSize);
        Optional<RecommendationCacheEntry> cached = cache.find(memberId)
                .filter(entry -> entry.candidates() != null && entry.candidates().size() >= size);
        if (cached.isPresent()) {
            return resolve(memberId, cached.get(), size);
        }

        Calculation calculated = calculate(memberId, size);
        cache.save(memberId, calculated.entry());
        return materialize(calculated.entry(), calculated.products(), size);
    }

    public int validateSize(Integer requestedSize) {
        int size = requestedSize == null ? properties.defaultSize() : requestedSize;
        if (size < 1 || size > properties.maxSize()) {
            throw new IllegalArgumentException("size는 1 이상 " + properties.maxSize() + " 이하여야 합니다.");
        }
        return size;
    }

    private Calculation calculate(Long memberId, int size) {
        Instant now = clock.instant();
        List<MemberProductInteraction> interactions;
        try {
            interactions = interactionRepository
                    .findAllByMemberIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(
                            memberId, now.minus(properties.profileWindow()));
        } catch (RuntimeException exception) {
            log.warn("회원 행동 조회 실패, 최신 상품으로 폴백 memberId={}", memberId, exception);
            return latest(memberId, size);
        }

        if (!interactions.isEmpty()) {
            try {
                Map<Long, ProductEmbeddingSnapshot> documents = embeddingIndex.findAll(
                        interactions.stream().map(MemberProductInteraction::getProductId).distinct().toList());
                Optional<RecommendationProfile> profile = profileCalculator.calculate(interactions, documents, now);
                if (profile.isPresent()) {
                    int candidateCount = candidateCount(size);
                    List<RecommendationCandidate> candidates = scorer.score(
                            profile.get(),
                            embeddingIndex.findGeneralNearest(profile.get().interestVector(), candidateCount));
                    if (!candidates.isEmpty()) {
                        Calculation personalized = validateCandidates(
                                memberId, RecommendationStrategy.PERSONALIZED, candidates);
                        return personalized.entry().candidates().isEmpty()
                                ? personalized
                                : fillPersonalizedShortfall(memberId, size, now, personalized);
                    }
                }
            } catch (RuntimeException exception) {
                if (exception instanceof RecommendationUnavailableException unavailable) {
                    throw unavailable;
                }
                log.warn("개인화 추천 계산 실패, 최신 상품으로 폴백 memberId={}", memberId, exception);
                return latest(memberId, size);
            }
        }

        try {
            Calculation popular = popular(memberId, size, now);
            return popular.entry().candidates().isEmpty() ? latest(memberId, size) : popular;
        } catch (RuntimeException exception) {
            if (exception instanceof RecommendationUnavailableException unavailable) {
                throw unavailable;
            }
            log.warn("인기 추천 계산 실패, 최신 상품으로 폴백 memberId={}", memberId, exception);
            return latest(memberId, size);
        }
    }

    private Calculation popular(Long memberId, int size, Instant now) {
        List<MemberProductInteraction> signals = interactionRepository.findPopularitySignals(
                now.minus(properties.popularPurchaseWindow()),
                now.minus(properties.popularCartWindow()));
        if (signals.isEmpty()) {
            return empty(RecommendationStrategy.POPULAR);
        }

        Map<Long, ProductEmbeddingSnapshot> documents = embeddingIndex.findAll(
                signals.stream().map(MemberProductInteraction::getProductId).distinct().toList());
        int candidateCount = candidateCount(size);
        List<RecommendationCandidate> candidates = popularCalculator.calculate(
                signals, documents, size, candidateCount);
        if (candidates.isEmpty()) {
            return empty(RecommendationStrategy.POPULAR);
        }
        return validateCandidates(memberId, RecommendationStrategy.POPULAR, candidates);
    }

    private Calculation fillPersonalizedShortfall(
            Long memberId,
            int size,
            Instant now,
            Calculation personalized) {
        if (personalized.entry().candidates().size() >= size) {
            return personalized;
        }

        Calculation popular;
        try {
            popular = popular(memberId, size, now);
        } catch (RuntimeException exception) {
            log.warn("인기 추천 부족분 계산 실패, 검증된 개인화 후보만 반환 memberId={}", memberId, exception);
            return personalized;
        }

        LinkedHashMap<Long, Candidate> mergedCandidates = new LinkedHashMap<>();
        personalized.entry().candidates().forEach(
                candidate -> mergedCandidates.putIfAbsent(candidate.productId(), candidate));
        for (Candidate candidate : popular.entry().candidates()) {
            if (mergedCandidates.size() >= size) {
                break;
            }
            mergedCandidates.putIfAbsent(
                    candidate.productId(),
                    new Candidate(candidate.productId(), RecommendationReason.POPULAR));
        }

        LinkedHashMap<Long, CoreProductCard> mergedProducts = new LinkedHashMap<>();
        personalized.products().forEach(
                product -> mergedProducts.putIfAbsent(product.productId(), product));
        popular.products().forEach(
                product -> mergedProducts.putIfAbsent(product.productId(), product));

        return new Calculation(
                new RecommendationCacheEntry(
                        RecommendationStrategy.PERSONALIZED,
                        List.copyOf(mergedCandidates.values())),
                List.copyOf(mergedProducts.values()));
    }

    private Calculation validateCandidates(
            Long memberId,
            RecommendationStrategy strategy,
            List<RecommendationCandidate> candidates) {
        List<CoreProductCard> validProducts = coreClient.validate(
                memberId, candidates.stream().map(RecommendationCandidate::productId).toList());
        Map<Long, RecommendationCandidate> ranking = new LinkedHashMap<>();
        candidates.forEach(candidate -> ranking.putIfAbsent(candidate.productId(), candidate));
        Map<Long, CoreProductCard> validById = new HashMap<>();
        validProducts.forEach(product -> validById.put(product.productId(), product));
        List<Candidate> validCandidates = ranking.values().stream()
                .filter(candidate -> validById.containsKey(candidate.productId()))
                .map(candidate -> new Candidate(candidate.productId(), candidate.reasonCode()))
                .toList();
        return new Calculation(
                new RecommendationCacheEntry(strategy, validCandidates),
                List.copyOf(validProducts));
    }

    private Calculation latest(Long memberId, int size) {
        List<CoreProductCard> latest = coreClient.latest(memberId, candidateCount(size));
        return new Calculation(
                new RecommendationCacheEntry(
                        RecommendationStrategy.LATEST,
                        latest.stream()
                        .map(product -> new Candidate(product.productId(), RecommendationReason.LATEST))
                        .toList()),
                List.copyOf(latest));
    }

    private RecommendationResult resolve(
            Long memberId, RecommendationCacheEntry entry, int size) {
        if (entry.candidates().isEmpty()) {
            return new RecommendationResult(entry.strategy(), List.of());
        }
        List<Long> ids = entry.candidates().stream().map(Candidate::productId).toList();
        List<CoreProductCard> products = coreClient.validate(memberId, ids);
        return materialize(entry, products, size);
    }

    private RecommendationResult materialize(
            RecommendationCacheEntry entry,
            List<CoreProductCard> products,
            int size) {
        Map<Long, CoreProductCard> productsById = new HashMap<>();
        products.forEach(product -> productsById.put(product.productId(), product));

        List<RecommendationResult.Item> items = new ArrayList<>();
        for (Candidate candidate : entry.candidates()) {
            CoreProductCard product = productsById.get(candidate.productId());
            if (product != null) {
                items.add(new RecommendationResult.Item(
                        product.productId(), product.name(), product.imageUrl(), product.price(),
                        product.category(), product.remainQuantity(), candidate.reasonCode()));
            }
            if (items.size() == size) {
                break;
            }
        }
        return new RecommendationResult(entry.strategy(), List.copyOf(items));
    }

    private int candidateCount(int size) {
        return Math.min(size * properties.candidateMultiplier(), properties.candidateMax());
    }

    private Calculation empty(RecommendationStrategy strategy) {
        return new Calculation(new RecommendationCacheEntry(strategy, List.of()), List.of());
    }

    private record Calculation(RecommendationCacheEntry entry, List<CoreProductCard> products) {
    }
}
