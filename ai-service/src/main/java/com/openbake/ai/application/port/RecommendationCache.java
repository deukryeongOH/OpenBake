package com.openbake.ai.application.port;

import com.openbake.ai.application.RecommendationCacheEntry;
import java.util.Optional;

public interface RecommendationCache {

    Optional<RecommendationCacheEntry> find(Long memberId);

    void save(Long memberId, RecommendationCacheEntry entry);
}
