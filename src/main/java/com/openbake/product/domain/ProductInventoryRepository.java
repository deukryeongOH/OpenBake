package com.openbake.product.domain;


public interface ProductInventoryRepository {
    ProductInventory save(ProductInventory productInventory);

    ProductInventory findByProductId(Long productId);

    void delete(ProductInventory productInventory);

    int adjustTotalQuantity(Long productId, int newTotal);

    int rollbackStock(Long productId, int quantity);

    int decreaseStock(Long productId, int quantity);

    // 드롭 진행 중 Redis 카운터 값을 DB에 반영한다. 증분이 아니라 절대값 대입이라 중복 실행돼도 결과가 같다.
    int syncRemainQuantity(Long productId, int remainQuantity);

    // 총 수량만 필요한 경로용 스칼라 조회
    int findTotalQuantity(Long productId);
}
