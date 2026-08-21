package com.openbake.product.application;

import com.openbake.product.application.dto.ProductInfoResult;
import com.openbake.product.application.port.ProductSearchPort;
import com.openbake.product.application.port.SemanticSearchPort;
import com.openbake.product.application.port.SemanticSearchPort.SemanticCandidate;
import com.openbake.product.domain.Category;
import com.openbake.product.domain.Product;
import com.openbake.product.domain.ProductInventory;
import com.openbake.product.domain.ProductInventoryRepository;
import com.openbake.product.domain.ProductRepository;
import com.openbake.product.domain.ProductStatus;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class ProductSearchService {

    private final ProductSearchPort productSearchPort;
    private final ProductRepository productRepository;
    private final ProductInventoryRepository productInventoryRepository;
    private final SemanticSearchPort semanticSearchPort;
    private final SearchProperties searchProperties;
    private final Clock clock;
    private final Executor semanticSearchExecutor;

    // Executor 빈이 둘 이상이라 @Qualifier가 필요한데, Lombok은 lombok.config 없이는
    // 필드의 @Qualifier를 생성자 파라미터로 복사하지 않는다. 그래서 생성자를 직접 쓴다.
    public ProductSearchService(
            ProductSearchPort productSearchPort,
            ProductRepository productRepository,
            ProductInventoryRepository productInventoryRepository,
            SemanticSearchPort semanticSearchPort,
            SearchProperties searchProperties,
            Clock clock,
            @Qualifier("semanticSearchExecutor") Executor semanticSearchExecutor) {
        this.productSearchPort = productSearchPort;
        this.productRepository = productRepository;
        this.productInventoryRepository = productInventoryRepository;
        this.semanticSearchPort = semanticSearchPort;
        this.searchProperties = searchProperties;
        this.clock = clock;
        this.semanticSearchExecutor = semanticSearchExecutor;
    }

    /**
     * ES에서 키워드+카테고리로 상품 ID를 검색한 뒤,
     * RDB에서 상세 데이터(재고 포함)를 조회하여 반환한다.
     * 검색어가 있으면 의미 검색을 키워드 검색과 병렬로 조회해 RRF로 병합한다.
     * ES 장애 시 RDB LIKE 검색으로 fallback.
     */
    @Transactional(readOnly = true)
    @CircuitBreaker(name = "elasticsearch", fallbackMethod = "searchFallback")
    public Page<ProductInfoResult> search(String keyword, String categoryName, Pageable pageable) {
        Category category = categoryName != null ? Category.valueOf(categoryName.toUpperCase()) : null;

        if (keyword == null || keyword.isBlank()) {
            return searchWithoutKeyword(category, pageable);
        }
        return searchWithKeyword(keyword, category, pageable);
    }

    @Transactional(readOnly = true)
    @CircuitBreaker(name = "elasticsearch", fallbackMethod = "autocompleteFallback")
    public List<String> autocomplete(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return List.of();
        }
        return productSearchPort.autocomplete(prefix, 10);
    }

    /** 검색어가 없는 홈 목록 조회 — 이슈 6 이전과 완전히 동일하게 동작한다. */
    private Page<ProductInfoResult> searchWithoutKeyword(Category category, Pageable pageable) {
        List<Long> productIds = productSearchPort.searchIds(null, category, pageable);

        if (productIds.isEmpty()) {
            return Page.empty(pageable);
        }

        List<ProductInfoResult> results = productIds.stream()
                .map(productRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(this::toResult)
                .toList();

        long totalHits = productSearchPort.countBySearch(null, category);

        return new PageImpl<>(results, pageable, totalHits);
    }

    /** 검색어가 있는 경우 — BM25와 의미 검색을 RRF로 병합한다. */
    private Page<ProductInfoResult> searchWithKeyword(String keyword, Category category, Pageable pageable) {
        int poolSize = computePoolSize(pageable);
        if (poolSize <= 0) {
            return Page.empty(pageable);
        }

        // 의미 검색은 OpenAI 임베딩 왕복을 포함해 가장 느리다.
        // 키워드 검색과 겹쳐 실행해 두 지연이 더해지지 않게 한다.
        CompletableFuture<List<Long>> semanticFuture =
                CompletableFuture.supplyAsync(
                        () -> fetchSemanticIds(keyword, category, poolSize), semanticSearchExecutor);

        List<Long> lexicalPoolIds = productSearchPort.searchIds(keyword, category, PageRequest.of(0, poolSize));
        long lexicalTotal = productSearchPort.countBySearch(keyword, category);
        List<Long> semanticIds = awaitSemantic(semanticFuture);

        List<Long> mergedIds = RrfMerger.merge(lexicalPoolIds, semanticIds, searchProperties.rrf().k());

        Set<Long> lexicalIdSet = new HashSet<>(lexicalPoolIds);
        long semanticOnlyCount = mergedIds.stream().filter(id -> !lexicalIdSet.contains(id)).count();
        long totalHits = lexicalTotal + semanticOnlyCount;

        List<Long> pageIds = slice(mergedIds, pageable);
        List<ProductInfoResult> results = fetchSellableDetails(pageIds);

        return new PageImpl<>(results, pageable, totalHits);
    }

    private int computePoolSize(Pageable pageable) {
        long offset = pageable.isPaged() ? pageable.getOffset() : 0;
        int pageSize = pageable.isPaged() ? pageable.getPageSize() : searchProperties.semantic().candidateMax();
        long raw = (offset + pageSize) * (long) searchProperties.semantic().poolMultiplier();
        return (int) Math.max(0, Math.min(raw, searchProperties.semantic().candidateMax()));
    }

    /**
     * 의미 검색 결과를 기다린다. 어떤 실패도 검색 자체를 실패시키지 않는다.
     * 정상 경로에서는 adapter의 서킷 브레이커가 이미 빈 목록으로 폴백하므로 여기까지 오지 않지만,
     * 스레드 중단이나 예상 밖 오류에도 키워드 결과만으로 응답하도록 마지막 방어선을 둔다.
     */
    private List<Long> awaitSemantic(CompletableFuture<List<Long>> future) {
        try {
            return future.join();
        } catch (RuntimeException exception) {
            log.warn("의미 검색 결과 수신 실패 — 키워드 결과만으로 응답한다.", exception);
            return List.of();
        }
    }

    private List<Long> fetchSemanticIds(String keyword, Category category, int poolSize) {
        if (!searchProperties.semantic().enabled()) {
            return List.of();
        }
        List<SemanticCandidate> candidates = semanticSearchPort.findNearest(keyword, category, poolSize);
        return candidates.stream()
                .sorted(Comparator.comparingInt(SemanticCandidate::rank))
                .map(SemanticCandidate::productId)
                .toList();
    }

    private List<Long> slice(List<Long> mergedIds, Pageable pageable) {
        if (!pageable.isPaged()) {
            return mergedIds;
        }
        int from = (int) Math.min(pageable.getOffset(), mergedIds.size());
        int to = (int) Math.min(pageable.getOffset() + pageable.getPageSize(), mergedIds.size());
        return mergedIds.subList(from, to);
    }

    /**
     * 병합된 productId 목록으로 상품·재고를 일괄 조회한다.
     * 삭제·품절·픽업 불가 상품은 여기서 제외한다 — 최종 판정은 언제나 core DB다.
     */
    private List<ProductInfoResult> fetchSellableDetails(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }

        LocalDate today = LocalDate.now(clock);
        Map<Long, Product> sellableById = new HashMap<>();
        for (Product product : productRepository.findAllByIdWithPickupDates(productIds)) {
            if (product.getStatus() != ProductStatus.SELLING) {
                continue;
            }
            boolean pickupAvailable = product.getPickUpAvailableDates().stream()
                    .anyMatch(date -> !date.isBefore(today));
            if (pickupAvailable) {
                sellableById.put(product.getId(), product);
            }
        }
        if (sellableById.isEmpty()) {
            return List.of();
        }

        Map<Long, ProductInventory> inventoryById = new HashMap<>();
        productInventoryRepository.findAllByProductIds(sellableById.keySet())
                .forEach(inventory -> inventoryById.put(inventory.getProductId(), inventory));

        List<ProductInfoResult> results = new ArrayList<>();
        for (Long productId : productIds) {
            Product product = sellableById.get(productId);
            ProductInventory inventory = inventoryById.get(productId);
            if (product != null && inventory != null) {
                results.add(toResult(product, inventory));
            }
        }
        return results;
    }

    /**
     * ES 장애 시 RDB LIKE 검색으로 대체.
     * 동의어/형태소 분석은 불가하지만 서비스 자체는 유지된다.
     */
    private Page<ProductInfoResult> searchFallback(String keyword, String categoryName,
                                                    Pageable pageable, Throwable t) {
        log.warn("ES 검색 실패 — RDB fallback 전환. reason={}", t.getMessage());
        Category category = categoryName != null ? Category.valueOf(categoryName.toUpperCase()) : null;
        List<Product> products = productRepository.fallbackSearch(keyword, category, pageable);
        List<ProductInfoResult> results = products.stream().map(this::toResult).toList();
        return new PageImpl<>(results, pageable, results.size());
    }

    /**
     * ES 장애 시 자동완성은 빈 리스트 반환.
     * edge_ngram 기반 자동완성은 RDB에서 대체 불가.
     */
    private List<String> autocompleteFallback(String prefix, Throwable t) {
        log.warn("ES 자동완성 실패 — 빈 리스트 반환. reason={}", t.getMessage());
        return List.of();
    }

    private ProductInfoResult toResult(Product product) {
        ProductInventory inventory = productInventoryRepository.findByProductId(product.getId());
        return toResult(product, inventory);
    }

    private ProductInfoResult toResult(Product product, ProductInventory inventory) {
        return ProductInfoResult.of(
                product.getName(), product.getDescription(), product.getImageUrl(),
                inventory.getTotalQuantity(), product.getPrice(),
                Set.copyOf(product.getPickUpAvailableDates()),
                product.getCategory(),
                product.getId(), inventory.getRemainQuantity(), product.getType(),
                product.getSellerId()
        );
    }
}
