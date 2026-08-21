package com.openbake.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openbake.ai.application.port.CoreProductSourceClient;
import com.openbake.ai.application.port.ProductEmbeddingIndex;
import com.openbake.ai.domain.ProductEmbeddingMetadataRepository;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EmbeddingReconciliationServiceTest {

    @Test
    void schedulesMissingAndDeletesSortedExtrasUpToLimit() {
        CoreProductSourceClient core = mock(CoreProductSourceClient.class);
        EmbeddingRecoveryScheduler scheduler = mock(EmbeddingRecoveryScheduler.class);
        ProductEmbeddingMetadataRepository metadata = mock(ProductEmbeddingMetadataRepository.class);
        ProductEmbeddingIndex index = mock(ProductEmbeddingIndex.class);
        RecoveryProperties properties = new RecoveryProperties(
                new RecoveryProperties.Backfill(100, Duration.ZERO),
                new RecoveryProperties.Reconcile(1),
                new RecoveryProperties.Metrics(Duration.ofSeconds(30)),
                new RecoveryProperties.Dlt(100, Set.of("product.changed.v1.dlt")));
        CoreProductSource product = new CoreProductSource(
                10L, "bread", "description", "MEAL_BREADS", "GENERAL");
        when(core.fetchPage(0, 100)).thenReturn(
                new CoreProductSourceClient.ProductSourcePage(List.of(product), 0, 1, 1, true));
        when(metadata.findById(10L)).thenReturn(Optional.empty());
        when(index.exists(10L)).thenReturn(false);
        when(index.findAllProductIds()).thenReturn(List.of(10L, 30L, 20L));
        var service = new EmbeddingReconciliationService(
                core, scheduler, metadata, index, properties);

        var result = service.reconcile();

        assertThat(result.missingCount()).isEqualTo(1);
        assertThat(result.extraCount()).isEqualTo(2);
        assertThat(result.actionCount()).isEqualTo(2);
        verify(scheduler).schedule(product, false);
        verify(index).delete(20L);
        verify(metadata).deleteById(20L);
    }
}
