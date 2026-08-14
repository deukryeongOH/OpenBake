package com.openbake.cart.infrastructure.client;

import com.openbake.cart.application.port.DropPort;
import com.openbake.cart.application.port.dto.DropInfo;
import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.drop.domain.entity.Drop;
import com.openbake.drop.domain.repository.DropRepository;
import com.openbake.product.domain.Product;
import com.openbake.product.domain.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;

/**
 * DropPort 구현체. drop 이 아직 같은 코어 안에 있어 저장소를 직접 호출한다.
 * drop 이 분리되면 이 클래스만 FeignClient 로 바뀌고 포트·서비스는 그대로다.
 *
 * drop 의 타입(Drop 엔티티)은 이 파일 밖으로 나가지 않는다.
 */
@Component
@RequiredArgsConstructor
public class DropClient implements DropPort {

    private final DropRepository dropRepository;
    private final ProductRepository productRepository;

    @Override
    public DropInfo getDrop(Long dropId) {
        Drop drop = dropRepository.findById(dropId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DROP_NOT_FOUND));

        Product product = productRepository.findById(drop.getProductId()).orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        return new DropInfo(
                drop.getId(),
                product.getSellerId(),
                product.getName(),
                product.getPrice(),
                product.getImageUrl(),
                //pickUpAvailableDate 는 지연 로딩 컬렉션이다. 참조를 그대로 넘기면
                //세션이 끝난 뒤 초기화를 시도하다 터지므로 여기서 복사해서 내보낸다.
                new HashSet<>(product.getPickUpAvailableDates())
        );
    }
}

