package com.openbake.product.infrastructure;

import com.openbake.product.domain.Product;
import com.openbake.product.domain.ProductRepository;
import com.openbake.product.domain.Type;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    public Long findSellerIdById(Long id) {
        return productJpaRepository.findSellerIdById(id);
    }

    @Override
    public Page<Product> findAllBySellerIdAndType(Long sellerId, Type type, Pageable pageable) {
        return productJpaRepository.findAllBySellerIdAndType(sellerId, type, pageable);
    }

    @Override
    public Page<Product> findAllByType(Type type, Pageable pageable) {
        return productJpaRepository.findAllByType(type, pageable);
    }
}
