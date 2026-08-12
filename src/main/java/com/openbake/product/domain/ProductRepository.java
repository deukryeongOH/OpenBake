package com.openbake.product.domain;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ProductRepository {
    void save(Product product);

    Optional<Product> findById(Long productId);

    void delete(Product product);

    Page<Product> findAllBySellerId(Long sellerId, Pageable pageable);

    Page<Product> findAll(Pageable pageable);

    Page<Product> searchByKeywordAndCategory(String keyword, Category category, Pageable pageable);
}
