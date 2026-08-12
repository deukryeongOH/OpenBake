package com.openbake.product.infrastructure;

import com.openbake.product.domain.ProductInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductInventoryJpaRepository extends JpaRepository<ProductInventory, Long> {

    // 비관적 락 및 낙관적 락 대신 조건부 단일 쿼리 UPDATE 사용
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ProductInventory pi SET pi.remainQuantity = pi.remainQuantity - :quantity WHERE pi.productId = :productId AND pi.remainQuantity >= :quantity")
    int decreaseStock(@Param("productId") Long productId, @Param("quantity") int quantity);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ProductInventory pi SET pi.remainQuantity = pi.remainQuantity + :quantity WHERE pi.productId = :productId AND pi.totalQuantity >= pi.remainQuantity + :quantity")
    int rollbackStock(@Param("productId") Long productId, @Param("quantity") int quantity);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ProductInventory pi SET pi.remainQuantity = pi.remainQuantity + (:newTotal - pi.totalQuantity), pi.totalQuantity = :newTotal " + "WHERE pi.productId = :productId AND pi.remainQuantity + (:newTotal - pi.totalQuantity) >= 0")
    int adjustTotalQuantity(@Param("productId") Long productId, @Param("newTotal") int newTotal);

}
