package com.openbake.ai.application;

import com.openbake.ai.application.port.CoreProductSourceClient;
import com.openbake.ai.application.port.ProductEmbeddingIndex;
import com.openbake.ai.domain.ProductEmbeddingMetadataRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingReconciliationService {

    private final CoreProductSourceClient coreProductSourceClient;
    private final EmbeddingRecoveryScheduler scheduler;
    private final ProductEmbeddingMetadataRepository metadataRepository;
    private final ProductEmbeddingIndex embeddingIndex;
    private final RecoveryProperties properties;

    public ReconciliationResult reconcile() {
        List<CoreProductSource> products = fetchAllProducts();
        Set<Long> coreIds = new HashSet<>();
        int missing = 0;
        int actions = 0;
        for (CoreProductSource product : products) {
            coreIds.add(product.productId());
            boolean metadataMissing = metadataRepository.findById(product.productId()).isEmpty();
            boolean documentMissing = !embeddingIndex.exists(product.productId());
            if (metadataMissing || documentMissing) {
                missing++;
                scheduler.schedule(product, false);
                actions++;
            }
        }

        List<Long> extras = embeddingIndex.findAllProductIds().stream()
                .filter(productId -> !coreIds.contains(productId))
                .sorted()
                .toList();
        int deleteCount = Math.min(extras.size(), properties.reconcile().maxDeletions());
        for (Long productId : extras.subList(0, deleteCount)) {
            embeddingIndex.delete(productId);
            metadataRepository.deleteById(productId);
            actions++;
        }
        ReconciliationResult result = new ReconciliationResult(
                products.size(), missing, extras.size(), actions);
        log.info("[Embedding reconciliation] inspectedCount={} missingCount={} extraCount={} actionCount={}",
                result.inspectedCount(), result.missingCount(), result.extraCount(), result.actionCount());
        return result;
    }

    private List<CoreProductSource> fetchAllProducts() {
        List<CoreProductSource> products = new ArrayList<>();
        int pageNumber = 0;
        while (true) {
            var page = coreProductSourceClient.fetchPage(pageNumber, properties.backfill().pageSize());
            products.addAll(page.content());
            if (page.last() || page.content().isEmpty()) {
                return products;
            }
            pageNumber++;
        }
    }

    public record ReconciliationResult(
            int inspectedCount, int missingCount, int extraCount, int actionCount) {
    }
}
