package com.openbake.order.infrastructure.client;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.drop.domain.entity.Drop;
import com.openbake.drop.domain.repository.DropRepository;
import com.openbake.order.application.port.DropPort;
import com.openbake.order.application.port.dto.DropInfo;
import com.openbake.product.domain.Product;
import com.openbake.product.domain.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;

//cart 에도 같은 이름의 어댑터가 있어 빈 이름을 명시한다(클래스 단순명이 겹치면 기동 실패).
@Component("orderDropClient")
@RequiredArgsConstructor
public class DropClient implements DropPort {
    private final DropRepository dropRepository;
    private final ProductRepository productRepository;

    /**
     * 드롭의 표시 정보는 전부 연결된 Product 에서 온다. 드롭은 시간·수량 제한만 갖는다.
     *
     * 그래서 productId 를 함께 내보내는 데 추가 쿼리가 들지 않는다 — 어차피 Product 를 읽는다.
     */
    @Override
    public DropInfo getDrop(Long dropId) {
        Drop drop = dropRepository.findById(dropId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DROP_NOT_FOUND));
        Product product = productRepository.findById(drop.getProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        return new DropInfo(
                drop.getId(),
                product.getId(),
                product.getSellerId(),
                product.getName(),
                product.getPrice(),
                product.getImageUrl(),
                //지연 로딩 컬렉션이라 참조를 그대로 넘기면 세션이 끝난 뒤 터진다. 복사해서 내보낸다.
                new HashSet<>(product.getPickUpAvailableDates())
        );
    }

    @Override
    public Long getProductId(Long dropId) {
        return dropRepository.findById(dropId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DROP_NOT_FOUND))
                .getProductId();
    }
}
