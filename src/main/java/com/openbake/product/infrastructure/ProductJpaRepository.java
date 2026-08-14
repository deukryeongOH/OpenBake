package com.openbake.product.infrastructure;

import com.openbake.product.domain.Category;
import com.openbake.product.domain.Product;
import com.openbake.product.domain.Type;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {

    @Query(value = "Select p From Product p WHERE p.sellerId = :sellerId")
    Page<Product> findAllBySellerId(@Param("sellerId") Long sellerId, Pageable pageable);

    List<Product> findAllBySellerId(Long sellerId);

    Long findSellerIdById(Long id);

    Page<Product> findAllBySellerIdAndType(Long sellerId, Type type, Pageable pageable);

    Page<Product> findAllByType(Type type, Pageable pageable);

}

