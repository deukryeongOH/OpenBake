package com.openbake.product.infrastructure;

import com.openbake.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {
    List<Product> findAllBySellerId(Long sellerId);

    // 비관적 락 및 낙관적 락 대신 조건부 단일 쿼리 UPDATE 사용
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Product p SET p.remainQuantity = p.remainQuantity - :quantity WHERE p.id = :productId AND p.remainQuantity >= :quantity")
    int decreaseStock(@Param("productId") Long productId, @Param("quantity") int quantity);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Product p SET p.remainQuantity = p.remainQuantity + :quantity WHERE p.id = :productId AND p.totalQuantity >= p.remainQuantity + :quantity")
    int rollbackStock(@Param("productId") Long productId, @Param("quantity") int quantity);
}

