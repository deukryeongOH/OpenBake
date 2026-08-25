package com.openbake.cart.infrastructure.client;

import com.openbake.cart.application.port.ProductPort;
import com.openbake.cart.application.port.dto.ProductInfo;
import com.openbake.common.exception.BusinessException;
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
 * ProductPort 구현체. product 가 아직 같은 코어 안에 있어 저장소를 직접 호출한다.
 * product 가 분리되면 이 클래스만 FeignClient 로 바뀌고 포트·서비스는 그대로다.
 *
 * product 의 타입(Product 엔티티)은 이 파일 밖으로 나가지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductClient implements ProductPort {

    private final ProductRepository productRepository;
    private final ProductInventoryRepository productInventoryRepository;

    @Override
    public Optional<ProductInfo> findProduct(Long productId) {
        //소프트 삭제된 상품은 없는 상품처럼 내려 장바구니에 남은 항목을 비활성으로 표시한다.
        return productRepository.findById(productId)
                .filter(product -> product.getStatus() != ProductStatus.DELETED)
                .map(product -> new ProductInfo(
                        product.getId(),
                        product.getSellerId(),
                        product.getName(),
                        product.getPrice(),
                        product.getImageUrl(),
                        //product 의 enum 은 여기서 판정으로 바꿔 내보낸다. 밖으로 나가지 않는다.
                        product.getType() == Type.GENERAL,
                        product.getStatus() == ProductStatus.SOLD_OUT,
                        //pickUpAvailableDates 는 지연 로딩 컬렉션이다. 참조를 그대로 넘기면
                        //세션이 끝난 뒤 초기화를 시도하다 터지므로 여기서 복사해서 내보낸다.
                        new HashSet<>(product.getPickUpAvailableDates()),
                        findRemainQuantity(product)
                ));
    }

    /**
     * 재고 행이 없으면 0으로 본다.
     *
     * product 저장소는 재고가 없을 때 PRODUCT_INVENTORY_NOT_FOUND 를 던진다.
     * 장바구니 조회는 여러 상품을 한 번에 그리므로, 한 상품의 재고 행이 비었다고
     * 화면 전체가 500 이 되면 안 된다. 0으로 낮춰 품절(비활성)로 표시한다.
     */
    private int findRemainQuantity(Product product) {
        try {
            return productInventoryRepository.findByProductId(product.getId()).getRemainQuantity();
        } catch (BusinessException e) {
            log.warn("상품 재고 행이 없어 품절로 처리합니다. productId={}", product.getId());
            return 0;
        }
    }
}
