package com.openbake.product.infrastructure;

import com.openbake.product.domain.Product;
import com.openbake.product.domain.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryAdapter implements ProductRepository {
    private final ProductJpaRepository productJpaRepository;

    @Override
    public void save(Product product) {
        productJpaRepository.save(product);
    }

    @Override
    public Optional<Product> findById(Long productId) {
        return productJpaRepository.findById(productId);
    }

    @Override
    public void delete(Product product) {
        productJpaRepository.delete(product);
    }

    @Override
    public List<Product> findAllBySellerId(Long sellerId) {
        return productJpaRepository.findAllBySellerId(sellerId);
    }

    @Override
    public List<Product> findAll() {
        return productJpaRepository.findAll();
    }

    @Override
    public int decreaseStock(Long productId, int quantity) {
        return productJpaRepository.decreaseStock(productId, quantity);
    }

    @Override
    public int rollbackStock(Long productId, int quantity) {
        return productJpaRepository.rollbackStock(productId, quantity);
    }
}
