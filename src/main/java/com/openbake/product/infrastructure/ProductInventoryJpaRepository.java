package com.openbake.product.infrastructure;

import com.openbake.product.domain.ProductInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

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

    // 총 수량만 필요한 경로(재고 카운터 초기화·드리프트 검출·롤백 상한 검사)용 스칼라 조회.
    // getProductInfo 는 상품·재고·픽업일 지연 컬렉션까지 로딩하므로 주기 실행 경로에는 과하다.
    @Query("SELECT pi.totalQuantity FROM ProductInventory pi WHERE pi.productId = :productId")
    Optional<Integer> findTotalQuantity(@Param("productId") Long productId);

    // 드롭 진행 중 Redis 카운터 값을 반영한다. 드롭당 1회만 실행되므로 경합이 없고,
    // 증분이 아닌 절대값 대입이라 중복 실행돼도 결과가 같다(멱등).
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ProductInventory pi SET pi.remainQuantity = :remainQuantity "
            + "WHERE pi.productId = :productId AND pi.remainQuantity <> :remainQuantity")
    int syncRemainQuantity(@Param("productId") Long productId, @Param("remainQuantity") int remainQuantity);

}
