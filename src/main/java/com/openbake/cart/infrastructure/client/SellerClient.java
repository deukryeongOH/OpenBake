package com.openbake.cart.infrastructure.client;

import com.openbake.cart.application.port.SellerPort;
import com.openbake.cart.application.port.dto.SellerInfo;
import com.openbake.seller.domain.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * SellerPort 구현체. seller 가 아직 같은 코어 안에 있어 저장소를 직접 호출한다.
 * seller 의 타입(Seller 엔티티)은 이 파일 밖으로 나가지 않는다.
 */
@Component
@RequiredArgsConstructor
public class SellerClient implements SellerPort {

    private final SellerRepository sellerRepository;

    @Override
    public Optional<SellerInfo> findSeller(Long sellerId) {
        return sellerRepository.findById(sellerId)
                .map(seller -> new SellerInfo(sellerId, seller.getBakeryName()));
    }
}
