package com.openbake.product.application;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.product.application.dto.ProductInfoCommand;
import com.openbake.product.application.dto.ProductInfoResult;
import com.openbake.product.domain.*;

import com.openbake.product.application.event.ProductIndexEvent;
import com.openbake.product.application.port.CurrentSellerPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductInventoryRepository productInventoryRepository;
    private final CurrentSellerPort currentSellerPort;
    private final ApplicationEventPublisher eventPublisher;

    // register product
    @Transactional
    public ProductInfoResult register(ProductInfoCommand command, Type type) {
        Long sellerId = currentSellerPort.getCurrentSellerId();

        Product product = Product.builder()
                .name(command.name())
                .description(command.description())
                .imageUrl(command.imageUrl())
                .price(command.price())
                .pickUpAvailableDates(command.pickupDates())
                .category(command.category())
                .type(type)
                .sellerId(sellerId)
                .build();

        ProductInventory productInventory = ProductInventory.builder()
                .remainQuantity(command.totalQuantity())
                .totalQuantity(command.totalQuantity())
                .build();

        productRepository.save(product);
        productInventoryRepository.save(productInventory);

        return ProductInfoResult.of(product.getName(), product.getDescription(), product.getImageUrl(),
                productInventory.getTotalQuantity(), product.getPrice(), product.getPickUpAvailableDates(), product.getCategory(),
                product.getId(), productInventory.getRemainQuantity(), product.getType(), product.getSellerId());
    }

    // register Drop product
    @Transactional
    public ProductInfoResult registerDropProduct(ProductInfoCommand command) {
        return register(command, Type.DROP);
    }

    // register General product
    @Transactional
    public ProductInfoResult registerGeneralProduct(ProductInfoCommand command) {
        ProductInfoResult result = register(command, Type.GENERAL);
        Product product = getProduct(result.productId());
        eventPublisher.publishEvent(ProductIndexEvent.saved(product));
        return result;
    }


    // update product
    @Transactional
    public ProductInfoResult updateProduct(ProductInfoCommand command, Long productId, Type type) {
        Long sellerId = currentSellerPort.getCurrentSellerId();

        validateSellerProduct(productId, sellerId); // 해당 상품이 판매자의 상품인지 확인

        Product product = getProduct(productId);

        if (product.getType() != type) {
            throw new BusinessException(ErrorCode.INVALID_PRODUCT_TYPE);
        }

        product.updateProduct(command); // 수량 외 필드만 엔티티로 저장
        productRepository.save(product);

        // 판매자가 수정하고 있을 때 사용자가 사고 판매자 수정 끝나면 사용자가 산 내용 반영 안됨.
        // 따라서 그 순간의 실제 DB의 값을 적용해야 함.
        if(productInventoryRepository.adjustTotalQuantity(productId, command.totalQuantity()) == 0){ // 수량은 원자적 UPDATE로 나중에
            throw new BusinessException(ErrorCode.INVALID_TOTAL_QUANTITY);
        }

        Product updated = getProduct(productId); // adjustTotalQuantity는 별도 UPDATE이기 떄문에 최신값 다시 조회 해야함.
        ProductInventory productInventory = productInventoryRepository.findByProductId(updated.getId());

        return ProductInfoResult.of(updated.getName(), updated.getDescription(), updated.getImageUrl(),
                productInventory.getTotalQuantity(), updated.getPrice(), updated.getPickUpAvailableDates(), updated.getCategory(),
                updated.getId(), productInventory.getRemainQuantity(), updated.getType(), updated.getSellerId());
    }

    // update Drop product
    @Transactional
    public ProductInfoResult updateDropProduct(ProductInfoCommand command, Long productId) {
        return updateProduct(command, productId, Type.DROP);
    }

    // update General product
    @Transactional
    public ProductInfoResult updateGeneralProduct(ProductInfoCommand command, Long productId) {
        ProductInfoResult result = updateProduct(command, productId, Type.GENERAL);
        Product updated = getProduct(productId);
        eventPublisher.publishEvent(ProductIndexEvent.saved(updated));
        return result;
    }

    // delete product
    @Transactional
    public void deleteProduct(Long productId, Type type) {
        Long sellerId = currentSellerPort.getCurrentSellerId();

        Product product = validateSellerProduct(productId, sellerId); // 해당 상품이 판매자의 상품인지 확인
        ProductInventory productInventory = productInventoryRepository.findByProductId(productId);

        if (product.getType() != type) {
            throw new BusinessException(ErrorCode.INVALID_PRODUCT_TYPE);
        }
        productInventoryRepository.delete(productInventory);
        productRepository.delete(product);

    }

    // delete Drop product
    @Transactional
    public void deleteDropProduct(Long productId) {
        deleteProduct(productId, Type.DROP);
    }

    // delete General product
    @Transactional
    public void deleteGeneralProduct(Long productId) {
        deleteProduct(productId, Type.GENERAL);
        eventPublisher.publishEvent(ProductIndexEvent.deleted(productId));
    }

    // 판매자가 등록한 일반 상품 리스트 반환
    @Transactional(readOnly = true)
    public Page<ProductInfoResult> getSellerProductList(Pageable pageable) {
        Long sellerId = currentSellerPort.getCurrentSellerId();

        return productRepository.findAllBySellerIdAndType(sellerId, Type.GENERAL, pageable).map(this::toResult);
    }

    @Transactional(readOnly = true)
    public Page<ProductInfoResult> getProductList(Pageable pageable) {
        return productRepository.findAllByType(Type.GENERAL, pageable).map(this::toResult);
    }

    @Transactional
    public int decreaseStock(Long productId, int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.QUANTITY_CAN_NOT_BE_MINUS);
        }

        if (productInventoryRepository.decreaseStock(productId, quantity) == 0) {
            throw new BusinessException(ErrorCode.INVALID_USER_SELECT_QUANTITY);
        }

        ProductInventory productInventory = productInventoryRepository.findByProductId(productId);

        return productInventory.getRemainQuantity();
    }

    @Transactional
    public int rollbackStock(Long productId, int quantity){
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.QUANTITY_CAN_NOT_BE_MINUS);
        }
        if (productInventoryRepository.rollbackStock(productId, quantity) == 0) {
            throw new BusinessException(ErrorCode.INVALID_TOTAL_QUANTITY);
        }

        ProductInventory productInventory = productInventoryRepository.findByProductId(productId);

        return productInventory.getRemainQuantity();
    }


    private Product validateSellerProduct(Long productId, Long sellerId) {
        Product product = getProduct(productId);

        // 판매자 본인의 상품인지 확인
        if (!Objects.equals(product.getSellerId(), sellerId)) {
            throw new BusinessException(ErrorCode.THIS_IS_NOT_YOURS);
        }

        return product;
    }

    private ProductInfoResult toResult(Product product) {
        ProductInventory productInventory = productInventoryRepository.findByProductId(product.getId());

        return ProductInfoResult.of(
                product.getName(), product.getDescription(), product.getImageUrl(), productInventory.getTotalQuantity(),
                product.getPrice(), Set.copyOf(product.getPickUpAvailableDates()), product.getCategory(),
                product.getId(), productInventory.getRemainQuantity(), product.getType(), product.getSellerId());
    }

    private Product getProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public ProductInfoResult getProductInfo(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        ProductInventory productInventory = productInventoryRepository.findByProductId(productId);

        return ProductInfoResult.of(product.getName(), product.getDescription(), product.getImageUrl(),
                productInventory.getTotalQuantity(), product.getPrice(), product.getPickUpAvailableDates(), product.getCategory(),
                product.getId(), productInventory.getRemainQuantity(), product.getType(), product.getSellerId());
    }


    @Transactional(readOnly = true)
    public List<ProductInfoResult> findProductListBySellerId(Long sellerId) {
        List<Product> productList = productRepository.findAllBySellerId(sellerId);

        return productList.stream()
                .map(product -> {
                    ProductInventory productInventory = productInventoryRepository.findByProductId(product.getId());
                    return ProductInfoResult.of(product.getName(), product.getDescription(), product.getImageUrl(), productInventory.getTotalQuantity(),
                            product.getPrice(), product.getPickUpAvailableDates(), product.getCategory(), product.getId(), productInventory.getRemainQuantity(),
                            product.getType(), product.getSellerId());
                })
                .toList();

    }

    @Transactional(readOnly = true)
    public Long getSellerIdByProductId(Long productId) {
        return productRepository.findSellerIdById(productId);
    }

    @Transactional(readOnly = true)
    public boolean isGeneralProduct(Long productId) {
        Product product = productRepository.findById(productId).orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        return product.getType() == Type.GENERAL;
    }
}
