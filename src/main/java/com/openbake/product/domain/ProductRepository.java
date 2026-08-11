package com.openbake.product.domain;


import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    void save(Product product);

    Optional<Product> findById(Long productId);

    void delete(Product product);

    List<Product> findAllBySellerId(Long sellerId);

    List<Product> findAll();

    int decreaseStock(Long productId, int quantity);

    int rollbackStock(Long productId, int quantity);

    int adjustTotalQuantity(Long productId, int newTotal);
}
