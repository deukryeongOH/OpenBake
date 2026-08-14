package com.openbake.product.domain;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    void save(Product product);

    Optional<Product> findById(Long productId);

    void delete(Product product);

    // 단순 조회 용
    List<Product> findAllBySellerId(Long sellerId);

    Long findSellerIdById(Long id);

    Page<Product> findAllBySellerIdAndType(Long sellerId, Type type, Pageable pageable);

    Page<Product> findAllByType(Type type, Pageable pageable);
}
