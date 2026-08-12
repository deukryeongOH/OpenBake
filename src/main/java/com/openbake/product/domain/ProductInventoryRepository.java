package com.openbake.product.domain;


public interface ProductInventoryRepository {
    ProductInventory save(ProductInventory productInventory);

    ProductInventory findByProductId(Long productId);

    void delete(ProductInventory productInventory);

    int adjustTotalQuantity(Long productId, int newTotal);

    int rollbackStock(Long productId, int quantity);

    int decreaseStock(Long productId, int quantity);
}
