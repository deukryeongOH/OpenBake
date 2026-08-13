package com.openbake.product.infrastructure;

import com.openbake.product.domain.Category;
import com.openbake.product.domain.Product;
import com.openbake.product.domain.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

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
    public Page<Product> findAllBySellerId(Long sellerId, Pageable pageable) {
        return productJpaRepository.findAllBySellerId(sellerId, pageable);
    }

    @Override
    public Page<Product> findAll(Pageable pageable) {
        return productJpaRepository.findAll(pageable);
    }

    @Override
    public Page<Product> searchByKeywordAndCategory(String keyword, Category category, Pageable pageable) {
        return productJpaRepository.searchByKeywordAndCategory(keyword, category, pageable);
    }
}
