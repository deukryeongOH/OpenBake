package com.openbake.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.openbake.product.application.dto.ProductInfoResult;
import com.openbake.product.application.port.ProductSearchPort;
import com.openbake.product.application.port.SemanticSearchPort;
import com.openbake.product.application.port.SemanticSearchPort.SemanticCandidate;
import com.openbake.product.domain.Product;
import com.openbake.product.domain.ProductInventory;
import com.openbake.product.domain.ProductInventoryRepository;
import com.openbake.product.domain.ProductRepository;
import com.openbake.product.domain.Type;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ProductSearchServiceTest {

    @Mock
    private ProductSearchPort productSearchPort;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductInventoryRepository productInventoryRepository;
    @Mock
    private SemanticSearchPort semanticSearchPort;

    private ProductSearchService service;

    // Executor 로 Runnable::run 을 주입해 호출 스레드에서 바로 실행시킨다.
    // 병렬성 자체는 프로덕션 설정의 관심사이고, 여기서는 병합 로직만 결정적으로 검증한다.
    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC);
        SearchProperties properties = new SearchProperties(
                new SearchProperties.Semantic(true, Duration.ofMillis(500), 2, 200),
                new SearchProperties.Rrf(60));
        service = new ProductSearchService(
                productSearchPort, productRepository, productInventoryRepository,
                semanticSearchPort, properties, clock, Runnable::run);
    }

    @Test
    void blankKeywordNeverCallsSemanticSearch() {
        given(productSearchPort.searchIds(eq(null), eq(null), any(Pageable.class))).willReturn(List.of());

        service.search("   ", null, PageRequest.of(0, 10));

        verify(semanticSearchPort, never()).findNearest(any(), any(), anyInt());
    }

    @Test
    void nullKeywordUsesOriginalLexicalOnlyPath() {
        given(productSearchPort.searchIds(eq(null), eq(null), any(Pageable.class)))
                .willReturn(List.of(1L));
        given(productSearchPort.countBySearch(null, null)).willReturn(1L);
        Product product = product(1L, LocalDate.parse("2026-08-25"));
        given(productRepository.findById(1L)).willReturn(java.util.Optional.of(product));
        given(productInventoryRepository.findByProductId(1L)).willReturn(inventory(1L, 3, 5));

        Page<ProductInfoResult> page = service.search(null, null, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(ProductInfoResult::productId).containsExactly(1L);
        verify(semanticSearchPort, never()).findNearest(any(), any(), anyInt());
        verify(productRepository, never()).findAllByIdWithPickupDates(any());
    }

    @Test
    void mergesLexicalAndSemanticOnlyResultsAndSortsByRrf() {
        Pageable pageable = PageRequest.of(0, 10);
        given(productSearchPort.searchIds(eq("bread"), eq(null), any(Pageable.class)))
                .willReturn(List.of(1L, 2L));
        given(productSearchPort.countBySearch("bread", null)).willReturn(2L);
        given(semanticSearchPort.findNearest(eq("bread"), eq(null), anyInt()))
                .willReturn(List.of(
                        new SemanticCandidate(3L, 1, 0.9),
                        new SemanticCandidate(1L, 2, 0.5)));

        LocalDate future = LocalDate.parse("2026-08-25");
        given(productRepository.findAllByIdWithPickupDates(any()))
                .willReturn(List.of(product(1L, future), product(2L, future), product(3L, future)));
        given(productInventoryRepository.findAllByProductIds(any()))
                .willReturn(List.of(inventory(1L, 3, 5), inventory(2L, 3, 5), inventory(3L, 3, 5)));

        Page<ProductInfoResult> page = service.search("bread", null, pageable);

        // 1은 양쪽 모두 있어 가장 위. 3(semantic 1위)이 2(lexical 2위)보다 위로 온다
        assertThat(page.getContent()).extracting(ProductInfoResult::productId)
                .containsExactly(1L, 3L, 2L);
        // lexicalTotal(2) + semantic-only 신규 발굴(1개: productId 3)
        assertThat(page.getTotalElements()).isEqualTo(3L);
        verify(productRepository, never()).findById(any());
    }

    @Test
    void excludesSoldOutAndPickupUnavailableProducts() {
        given(productSearchPort.searchIds(eq("bread"), eq(null), any(Pageable.class)))
                .willReturn(List.of(1L, 2L, 3L));
        given(productSearchPort.countBySearch("bread", null)).willReturn(3L);
        given(semanticSearchPort.findNearest(eq("bread"), eq(null), anyInt())).willReturn(List.of());

        Product soldOut = product(1L, LocalDate.parse("2026-08-25"));
        soldOut.markSoldOut();
        Product pickupExpired = product(2L, LocalDate.parse("2026-08-25"));
        pickupExpired.getPickUpAvailableDates().clear();
        pickupExpired.getPickUpAvailableDates().add(LocalDate.parse("2026-08-01"));
        Product sellable = product(3L, LocalDate.parse("2026-08-25"));

        given(productRepository.findAllByIdWithPickupDates(any()))
                .willReturn(List.of(soldOut, pickupExpired, sellable));
        given(productInventoryRepository.findAllByProductIds(any()))
                .willReturn(List.of(inventory(3L, 3, 5)));

        Page<ProductInfoResult> page = service.search("bread", null, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(ProductInfoResult::productId).containsExactly(3L);
    }

    @Test
    void semanticSearchDisabledSkipsCallAndBehavesLikeLexicalOnly() {
        SearchProperties disabled = new SearchProperties(
                new SearchProperties.Semantic(false, Duration.ofMillis(500), 2, 200),
                new SearchProperties.Rrf(60));
        service = new ProductSearchService(
                productSearchPort, productRepository, productInventoryRepository,
                semanticSearchPort, disabled,
                Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC),
                Runnable::run);

        given(productSearchPort.searchIds(eq("bread"), eq(null), any(Pageable.class)))
                .willReturn(List.of(1L));
        given(productSearchPort.countBySearch("bread", null)).willReturn(1L);
        given(productRepository.findAllByIdWithPickupDates(any()))
                .willReturn(List.of(product(1L, LocalDate.parse("2026-08-25"))));
        given(productInventoryRepository.findAllByProductIds(any()))
                .willReturn(List.of(inventory(1L, 3, 5)));

        Page<ProductInfoResult> page = service.search("bread", null, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(ProductInfoResult::productId).containsExactly(1L);
        assertThat(page.getTotalElements()).isEqualTo(1L);
        verify(semanticSearchPort, never()).findNearest(any(), any(), anyInt());
    }

    @Test
    void poolSizeGrowsWithDeepPagesButIsCappedAtCandidateMax() {
        // offset=100, pageSize=20 => (120)*2=240, candidateMax=200 이므로 200으로 잘려야 한다
        Pageable deepPage = PageRequest.of(5, 20);
        given(productSearchPort.searchIds(eq("bread"), eq(null), any(Pageable.class)))
                .willReturn(List.of());
        given(productSearchPort.countBySearch("bread", null)).willReturn(0L);
        given(semanticSearchPort.findNearest(eq("bread"), eq(null), eq(200))).willReturn(List.of());

        service.search("bread", null, deepPage);

        verify(productSearchPort).searchIds(eq("bread"), eq(null), eq(PageRequest.of(0, 200)));
        verify(semanticSearchPort).findNearest(eq("bread"), eq(null), eq(200));
    }

    @Test
    void firstPagePoolSizeIsOffsetPlusPageSizeTimesMultiplier() {
        // offset=0, pageSize=10 => (10)*2=20
        Pageable firstPage = PageRequest.of(0, 10);
        given(productSearchPort.searchIds(eq("bread"), eq(null), any(Pageable.class)))
                .willReturn(List.of());
        given(productSearchPort.countBySearch("bread", null)).willReturn(0L);
        given(semanticSearchPort.findNearest(eq("bread"), eq(null), eq(20))).willReturn(List.of());

        service.search("bread", null, firstPage);

        verify(productSearchPort).searchIds(eq("bread"), eq(null), eq(PageRequest.of(0, 20)));
        verify(semanticSearchPort).findNearest(eq("bread"), eq(null), eq(20));
    }

    @Test
    void secondPageSlicesMergedResultsWithoutRepeatingFirstPage() {
        given(productSearchPort.searchIds(eq("bread"), eq(null), any(Pageable.class)))
                .willReturn(List.of(1L, 2L, 3L, 4L));
        given(productSearchPort.countBySearch("bread", null)).willReturn(4L);
        given(semanticSearchPort.findNearest(eq("bread"), eq(null), anyInt())).willReturn(List.of());

        LocalDate future = LocalDate.parse("2026-08-25");
        given(productRepository.findAllByIdWithPickupDates(any())).willReturn(List.of(
                product(1L, future), product(2L, future), product(3L, future), product(4L, future)));
        given(productInventoryRepository.findAllByProductIds(any())).willReturn(List.of(
                inventory(1L, 1, 1), inventory(2L, 1, 1), inventory(3L, 1, 1), inventory(4L, 1, 1)));

        Page<ProductInfoResult> secondPage = service.search("bread", null, PageRequest.of(1, 2));

        assertThat(secondPage.getContent()).extracting(ProductInfoResult::productId)
                .containsExactly(3L, 4L);
    }

    private Product product(Long id, LocalDate pickupDate) {
        Product product = Product.builder()
                .name("product-" + id)
                .description("description")
                .imageUrl("https://example.test/" + id)
                .price(1_000)
                .sellerId(1L)
                .pickUpAvailableDates(Set.of(pickupDate))
                .category(com.openbake.product.domain.Category.MEAL_BREADS)
                .type(Type.GENERAL)
                .build();
        setId(product, id);
        return product;
    }

    private ProductInventory inventory(Long productId, int remain, int total) {
        return ProductInventory.builder()
                .productId(productId)
                .remainQuantity(remain)
                .totalQuantity(total)
                .build();
    }

    private void setId(Product product, Long id) {
        try {
            var field = Product.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(product, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    // 의미 검색이 키워드 검색과 겹쳐 실행되는지 — 두 지연이 더해지면 실패한다.
    void semanticSearchRunsConcurrentlyWithLexicalSearch() throws Exception {
        Duration delay = Duration.ofMillis(300);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            service = new ProductSearchService(
                    productSearchPort, productRepository, productInventoryRepository,
                    semanticSearchPort,
                    new SearchProperties(
                            new SearchProperties.Semantic(true, Duration.ofSeconds(5), 2, 200),
                            new SearchProperties.Rrf(60)),
                    Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC),
                    pool);

            given(productSearchPort.searchIds(eq("bread"), eq(null), any(Pageable.class)))
                    .willAnswer(invocation -> {
                        Thread.sleep(delay.toMillis());
                        return List.of(1L);
                    });
            given(productSearchPort.countBySearch("bread", null)).willReturn(1L);
            given(semanticSearchPort.findNearest(eq("bread"), eq(null), anyInt()))
                    .willAnswer(invocation -> {
                        Thread.sleep(delay.toMillis());
                        return List.of(new SemanticCandidate(2L, 1, 0.9));
                    });
            given(productRepository.findAllByIdWithPickupDates(any())).willReturn(List.of());

            long startedAt = System.nanoTime();
            service.search("bread", null, PageRequest.of(0, 10));
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

            // 순차라면 600ms 이상이 걸린다. 겹쳐 돌면 한 번의 지연에 가깝다.
            assertThat(elapsed).isLessThan(delay.multipliedBy(2));
        } finally {
            pool.shutdownNow();
        }
    }
}
