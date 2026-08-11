package com.openbake.product.application;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.product.application.dto.GeneralProductInfoCommand;
import com.openbake.product.application.dto.GeneralProductInfoResult;
import com.openbake.product.domain.Product;
import com.openbake.product.domain.ProductRepository;
import com.openbake.product.infrastructure.CurrentSellerPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CurrentSellerPort currentSellerPort;

    // register product
    @Transactional
    public GeneralProductInfoResult registerGeneralProduct(GeneralProductInfoCommand command) {
        Long sellerId = currentSellerPort.getCurrentSellerId();

        Product product = Product.builder()
                .name(command.name())
                .description(command.description())
                .imageUrl(command.imageUrl())
                .totalQuantity(command.totalQuantity())
                .remainQuantity(command.totalQuantity())
                .price(command.price())
                .pickUpAvailableDate(command.pickupDates())
                .category(command.category())
                .sellerId(sellerId)
                .build();

        productRepository.save(product);

        return GeneralProductInfoResult.of(command, product.getId(), product.getRemainQuantity());
    }

    // update product
    @Transactional
    public GeneralProductInfoResult updateGeneralProduct(GeneralProductInfoCommand command, Long productId) {
        Long sellerId = currentSellerPort.getCurrentSellerId();

        validateSellerProduct(productId, sellerId); // 해당 상품이 판매자의 상품인지 확인

        Product product = getProduct(productId);

        product.updateProduct(command); // 수량 외 필드만 엔티티로 저장
        productRepository.save(product);

        // 판매자가 수정하고 있을 때 사용자가 사고 판매자 수정 끝나면 사용자가 산 내용 반영 안됨.
        // 따라서 그 순간의 실제 DB의 값을 적용해야 함.
        if(productRepository.adjustTotalQuantity(productId, command.totalQuantity()) == 0){ // 수량은 원자적 UPDATE로 나중에
            throw new BusinessException(ErrorCode.INVALID_TOTAL_QUANTITY);
        }

        Product updated = getProduct(productId); // adjustTotalQuantity는 별도 UPDATE이기 떄문에 최신값 다시 조회 해야함.

        return GeneralProductInfoResult.of(command, productId, updated.getRemainQuantity());
    }

    // delete product
    @Transactional
    public void deleteGeneralProduct(Long productId) {
        Long sellerId = currentSellerPort.getCurrentSellerId();

        validateSellerProduct(productId, sellerId); // 해당 상품이 판매자의 상품인지 확인

        Product product = getProduct(productId);

        productRepository.delete(product);
    }

    // 판매자가 등록한 일반 상품 리스트 반환
    @Transactional(readOnly = true)
    public List<GeneralProductInfoResult> getSellerGeneralProductList() {
        Long sellerId = currentSellerPort.getCurrentSellerId();

        return productRepository.findAllBySellerId(sellerId).stream()
                .map(this::toResult)
                .toList(); // 없으면 빈 리스트 반환
    }

    @Transactional(readOnly = true)
    public List<GeneralProductInfoResult> getGeneralProductList() {
        return productRepository.findAll().stream()
                .map(this::toResult)
                .toList();
    }

    @Transactional // 일반 상품 재고 차감
    public void decreaseStock(Long productId, int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.QUANTITY_CAN_NOT_BE_MINUS);
        }
        if (productRepository.decreaseStock(productId, quantity) == 0) {
            throw new BusinessException(ErrorCode.INVALID_USER_SELECT_QUANTITY);
        }
    }

    @Transactional
    public void rollbackStock(Long productId, int quantity){
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.QUANTITY_CAN_NOT_BE_MINUS);
        }
        if (productRepository.rollbackStock(productId, quantity) == 0) {
            throw new BusinessException(ErrorCode.INVALID_TOTAL_QUANTITY);
        }
    }


    private void validateSellerProduct(Long productId, Long sellerId) {
        Product product = getProduct(productId);

        // 판매자 본인의 상품인지 확인
        if (!Objects.equals(product.getSellerId(), sellerId)) {
            throw new BusinessException(ErrorCode.THIS_IS_NOT_YOURS);
        }
    }

    private GeneralProductInfoResult toResult(Product product) {
        return GeneralProductInfoResult.of(GeneralProductInfoCommand.create(
                product.getName(), product.getDescription(), product.getImageUrl(), product.getTotalQuantity(),
                product.getPrice(), product.getPickUpAvailableDate(), product.getCategory()
        ), product.getId(), product.getRemainQuantity());
    }

    private Product getProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    }
}
