package com.openbake.product.domain;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    void save(Product product);

    List<Product> fallbackSearch(String keyword, Category category, Pageable pageable);

    Optional<Product> findById(Long productId);

    // 픽업 가능 날짜까지 함께 불러오는 일괄 조회 — 상태/픽업 가능 여부 판정은 호출부에서 한다.
    List<Product> findAllByIdWithPickupDates(Collection<Long> productIds);

    void delete(Product product);

    // 드롭 상품 조회 용
    List<Product> findDropProductListBySellerId(Long sellerId);

    Long findSellerIdById(Long id);

    Page<Product> findAllBySellerIdAndType(Long sellerId, Type type, Pageable pageable);

    Page<Product> findAllByType(Type type, Pageable pageable);

    List<Product> findAllByType(Type type);

    /**
     * ai-service 백필·정합성 대조가 열거하는 임베딩 대상.
     * 삭제된 상품은 제외한다 — 포함하면 reconcile이 삭제 상품의 벡터를 다시 만든다.
     */
    Page<Product> findAllIndexTargets(Pageable pageable);
}
