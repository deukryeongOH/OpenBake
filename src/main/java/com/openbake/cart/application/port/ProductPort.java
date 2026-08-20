package com.openbake.cart.application.port;

import com.openbake.cart.application.port.dto.ProductInfo;

import java.util.Optional;

/**
 * 일반 상품 조회 포트.
 *
 * cart 는 product 의 저장소/엔티티를 직접 보지 않고 이 인터페이스만 안다.
 * 지금 구현체는 같은 코어 안의 저장소를 호출하지만, product 가 분리되면
 * infrastructure 의 구현체만 FeignClient 로 갈아끼우면 된다.
 */
public interface ProductPort {

    //상품이 삭제됐으면 빈 값. 장바구니 조회는 이 경우를 비활성 항목으로 표시한다.
    Optional<ProductInfo> findProduct(Long productId);
}
