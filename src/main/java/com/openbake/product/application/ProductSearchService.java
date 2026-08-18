package com.openbake.product.application;

import com.openbake.product.application.dto.ProductInfoResult;
import com.openbake.product.application.port.ProductSearchPort;
import com.openbake.product.domain.Category;
import com.openbake.product.domain.Product;
import com.openbake.product.domain.ProductInventory;
import com.openbake.product.domain.ProductInventoryRepository;
import com.openbake.product.domain.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductSearchService {

    private final ProductSearchPort productSearchPort;
    private final ProductRepository productRepository;
    private final ProductInventoryRepository productInventoryRepository;

    /**
     * ES에서 키워드+카테고리로 상품 ID를 검색한 뒤,
     * RDB에서 상세 데이터(재고 포함)를 조회하여 반환한다.
     */
    @Transactional(readOnly = true)
    public Page<ProductInfoResult> search(String keyword, String categoryName, Pageable pageable) {
        Category category = categoryName != null ? Category.valueOf(categoryName.toUpperCase()) : null;

        // 1. ES에서 매칭되는 상품 ID 검색
        List<Long> productIds = productSearchPort.searchIds(keyword, category, pageable);

        if (productIds.isEmpty()) {
            return Page.empty(pageable);
        }

        // 2. RDB에서 상세 데이터 조회 (재고 포함)
        List<ProductInfoResult> results = productIds.stream()
                .map(productRepository::findById)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .map(this::toResult)
                .toList();

        // 3. ES의 전체 건수로 페이지 정보 구성
        long totalHits = productSearchPort.countBySearch(keyword, category);

        return new PageImpl<>(results, pageable, totalHits);
    }

    @Transactional(readOnly = true)
    public List<String> autocomplete(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return List.of();
        }
        return productSearchPort.autocomplete(prefix, 10);
    }

    private ProductInfoResult toResult(Product product) {
        ProductInventory inventory = productInventoryRepository.findByProductId(product.getId());

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
