package com.openbake.product.infrastructure;

import com.openbake.drop.application.dto.DropInfoCommand;
import com.openbake.drop.application.dto.DropInfoResult;
import com.openbake.drop.application.dto.DropProductInfoResult;
import com.openbake.drop.application.port.ProductPort;
import com.openbake.product.application.ProductService;
import com.openbake.product.application.dto.ProductInfoCommand;
import com.openbake.product.application.dto.ProductInfoResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ProductAdapter implements ProductPort {

    private final ProductService productService;

    @Override
    public DropInfoResult registerProduct(DropInfoCommand command) {
        ProductInfoCommand request = new ProductInfoCommand(
                command.name(),
                command.description(),
                command.image(),
                command.totalQuantity(),
                command.price(),
                command.pickupDates(),
                command.category()
        );

        ProductInfoResult result = productService.registerDropProduct(request);

        return DropInfoResult.of(command.dropStart(), command.dropEnd(),
                command.limitQuantity(), command.dropStatus(), result.name(),
                result.description(), result.imageUrl(), result.pickUpAvailableDates(), result.price(),
                result.totalQuantity(), result.remainQuantity(), result.sellerId(), result.productId()
        );
    }


    @Override
    public DropProductInfoResult getProductInfo(Long productId) {
        ProductInfoResult result = productService.getProductInfo(productId);

        return DropProductInfoResult.of(result.name(), result.description(), result.imageUrl(),
                result.pickUpAvailableDates(), result.price(), result.totalQuantity(), result.remainQuantity(),
                result.sellerId(), result.productId()
        );
    }

    @Override
    public List<DropProductInfoResult> findProductListBySellerId(Long sellerId) {
        List<ProductInfoResult> productList = productService.findProductListBySellerId(sellerId);

        return productList.stream()
                .map(productInfoResult -> DropProductInfoResult.of(productInfoResult.name(),
                        productInfoResult.description(), productInfoResult.imageUrl(), productInfoResult.pickUpAvailableDates(),
                        productInfoResult.price(), productInfoResult.totalQuantity(), productInfoResult.remainQuantity(),
                        productInfoResult.sellerId(), productInfoResult.productId()))
                .toList();
    }

    @Override
    public DropProductInfoResult updateProduct(Long productId, DropInfoCommand command) {
        ProductInfoCommand productInfoCommand = ProductInfoCommand.create(command.name(), command.description(), command.image(), command.totalQuantity(),
                command.price(), command.pickupDates(), command.category());

        ProductInfoResult result = productService.updateDropProduct(productInfoCommand, productId);

        return DropProductInfoResult.of(result.name(), result.description(), result.imageUrl(), result.pickUpAvailableDates(), result.price(),
                result.totalQuantity(), result.remainQuantity(), result.sellerId(), result.productId());
    }

    @Override
    public void deleteDropProduct(Long productId) {
        productService.deleteDropProduct(productId);
    }

    @Override
    public int decreaseQuantity(Long productId, int selectQuantity) {
        return productService.decreaseStock(productId, selectQuantity);
    }

    @Override
    public int rollbackQuantity(Long productId, int selectQuantity) {
        return productService.rollbackStock(productId, selectQuantity);
    }

    @Override
    public Long getSellerIdByProductId(Long productId) {
        return productService.getSellerIdByProductId(productId);
    }

    @Override
    public boolean isGeneralProduct(Long productId) {
        return productService.isGeneralProduct(productId);
    }
}
