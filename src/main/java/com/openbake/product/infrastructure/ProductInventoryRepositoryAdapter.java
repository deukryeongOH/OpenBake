package com.openbake.product.infrastructure;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.product.domain.ProductInventory;
import com.openbake.product.domain.ProductInventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProductInventoryRepositoryAdapter implements ProductInventoryRepository {

    private final ProductInventoryJpaRepository productInventoryJpaRepository;

    @Override
    public ProductInventory save(ProductInventory productInventory) {
        return productInventoryJpaRepository.save(productInventory);
    }

    @Override
    public ProductInventory findByProductId(Long productId) {
        return productInventoryJpaRepository.findById(productId).orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_INVENTORY_NOT_FOUND));
    }

    @Override
    public void delete(ProductInventory productInventory) {
        productInventoryJpaRepository.delete(productInventory);
    }

    @Override
    public int adjustTotalQuantity(Long productId, int newTotal) {
        return productInventoryJpaRepository.adjustTotalQuantity(productId, newTotal);
    }

    @Override
    public int rollbackStock(Long productId, int quantity) {
        return productInventoryJpaRepository.rollbackStock(productId, quantity);
    }

    @Override
    public int decreaseStock(Long productId, int quantity) {
        return productInventoryJpaRepository.decreaseStock(productId, quantity);
    }

    @Override
    public int syncRemainQuantity(Long productId, int remainQuantity) {
        return productInventoryJpaRepository.syncRemainQuantity(productId, remainQuantity);
    }

    @Override
    public int findTotalQuantity(Long productId) {
        return productInventoryJpaRepository.findTotalQuantity(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_INVENTORY_NOT_FOUND));
    }
}
