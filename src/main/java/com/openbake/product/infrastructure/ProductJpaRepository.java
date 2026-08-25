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
import java.time.LocalDate;
import java.util.Collection;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {

    @Query("""
            SELECT DISTINCT p FROM Product p
            JOIN FETCH p.pickUpAvailableDates pickupDate
            WHERE p.id IN :productIds
              AND p.type = com.openbake.product.domain.Type.GENERAL
              AND p.status = com.openbake.product.domain.ProductStatus.SELLING
              AND pickupDate >= :today
            """)
    List<Product> findRecommendationCandidates(
            @Param("productIds") Collection<Long> productIds,
            @Param("today") LocalDate today);

    @Query("""
            SELECT DISTINCT new com.openbake.product.application.RecommendationProduct(
                p.id, p.name, p.imageUrl, p.price, p.category, inventory.remainQuantity)
            FROM Product p
            JOIN ProductInventory inventory ON inventory.productId = p.id
            JOIN p.pickUpAvailableDates pickupDate
            WHERE p.type = com.openbake.product.domain.Type.GENERAL
              AND p.status = com.openbake.product.domain.ProductStatus.SELLING
              AND inventory.remainQuantity >= 1
              AND pickupDate >= :today
              AND (:sellerId IS NULL OR p.sellerId <> :sellerId)
            ORDER BY p.id DESC
            """)
    List<com.openbake.product.application.RecommendationProduct> findLatestRecommendationCandidates(
            @Param("today") LocalDate today,
            @Param("sellerId") Long sellerId,
            Pageable pageable);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.pickUpAvailableDates "
         + "WHERE p.status = com.openbake.product.domain.ProductStatus.SELLING "
         + "AND (:keyword IS NULL OR p.name LIKE %:keyword%) "
         + "AND (:category IS NULL OR p.category = :category)")
    List<Product> fallbackSearch(@Param("keyword") String keyword,
                                @Param("category") Category category,
                                Pageable pageable);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.pickUpAvailableDates "
         + "WHERE p.id IN :productIds")
    List<Product> findAllByIdWithPickupDates(@Param("productIds") Collection<Long> productIds);

    @Query(value = "Select p From Product p WHERE p.sellerId = :sellerId")
    Page<Product> findAllBySellerId(@Param("sellerId") Long sellerId, Pageable pageable);

    List<Product> findAllBySellerId(Long sellerId);

    Long findSellerIdById(Long id);

    @Query("SELECT p FROM Product p "
         + "WHERE p.sellerId = :sellerId AND p.type = :type "
         + "AND p.status <> com.openbake.product.domain.ProductStatus.DELETED")
    Page<Product> findAllBySellerIdAndType(@Param("sellerId") Long sellerId,
                                           @Param("type") Type type,
                                           Pageable pageable);

    Page<Product> findAllByType(Type type, Pageable pageable);

    List<Product> findAllByType(Type type);

    @Query("SELECT p FROM Product p "
         + "WHERE p.status <> com.openbake.product.domain.ProductStatus.DELETED")
    Page<Product> findAllIndexTargets(Pageable pageable);

}
