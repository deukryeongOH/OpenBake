package com.openbake.order.infrastructure.client;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.order.application.port.ProductPort;
import com.openbake.order.application.port.dto.ProductInfo;
import com.openbake.product.application.ProductService;
import com.openbake.product.domain.Product;
import com.openbake.product.domain.ProductInventoryRepository;
import com.openbake.product.domain.ProductRepository;
import com.openbake.product.domain.ProductStatus;
import com.openbake.product.domain.Type;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Optional;

/**
 * ProductPort 구현체. product 가 아직 같은 코어 안에 있어 서비스·저장소를 직접 호출한다.
 * product 가 분리되면 이 클래스만 FeignClient 로 바뀌고 포트·서비스는 그대로다.
 *
 * product 의 타입(Product 엔티티, Type, ProductStatus)은 이 파일 밖으로 나가지 않는다.
 */
//cart 에도 같은 이름의 어댑터가 있어 빈 이름을 명시한다(클래스 단순명이 겹치면 기동 실패).
@Slf4j
@Component("orderProductClient")
@RequiredArgsConstructor
public class ProductClient implements ProductPort {

    private final ProductService productService;
    private final ProductRepository productRepository;
    private final ProductInventoryRepository productInventoryRepository;

    @Override
    public Optional<ProductInfo> findProduct(Long productId) {
        return productRepository.findById(productId)
                .filter(product -> product.getStatus() != ProductStatus.DELETED)
                .map(product -> new ProductInfo(
                        product.getId(),
                        product.getSellerId(),
                        product.getName(),
                        product.getPrice(),
                        product.getImageUrl(),
                        product.getType() == Type.GENERAL,
                        product.getStatus() == ProductStatus.SOLD_OUT,
                        //지연 로딩 컬렉션이라 참조를 그대로 넘기면 세션이 끝난 뒤 터진다. 복사해서 내보낸다.
                        new HashSet<>(product.getPickUpAvailableDates()),
                        findRemainQuantity(product.getId())
                ));
    }

    /**
     * ProductService.decreaseStock 은 @Transactional(REQUIRED) 이라 호출한 트랜잭션에
     * 그대로 참여한다. 조건부 UPDATE 가 0건이면 DR018 을 던지므로 여기서 잡아 false 로 바꾼다.
     *
     * 재고 부족은 order 입장에서 예외가 아니라 <b>분기해야 할 결과</b>다.
     * 결제가 이미 성공한 뒤라 이 false 를 받으면 환불로 되돌려야 한다.
     */
    @Override
    public boolean decreaseStock(Long productId, int quantity) {
        try {
            productService.decreaseStock(productId, quantity);
            return true;
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.INVALID_USER_SELECT_QUANTITY) {
                log.info("재고 차감 실패 — 남은 수량 부족. productId={}, quantity={}", productId, quantity);
                return false;
            }
            throw e;
        }
    }

    @Override
    public void rollbackStock(Long productId, int quantity) {
        productService.rollbackStock(productId, quantity);
    }

    /**
     * 재고 행이 없으면 0으로 본다.
     *
     * 저장소는 재고가 없을 때 PRODUCT_INVENTORY_NOT_FOUND 를 던지는데, 그건
     * 주문할 수 없다는 뜻이지 서버 오류가 아니다. 0으로 낮춰 품절과 같게 다룬다.
     */
    private int findRemainQuantity(Long productId) {
        try {
            return productInventoryRepository.findByProductId(productId).getRemainQuantity();
        } catch (BusinessException e) {
            log.warn("상품 재고 행이 없어 품절로 처리합니다. productId={}", productId);
            return 0;
        }
    }
}
