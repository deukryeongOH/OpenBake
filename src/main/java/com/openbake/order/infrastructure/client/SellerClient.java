package com.openbake.order.infrastructure.client;

import com.openbake.order.application.port.SellerPort;
import com.openbake.order.application.port.dto.SellerInfo;
import com.openbake.seller.application.CurrentSellerProvider;
import com.openbake.seller.domain.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * SellerPort 구현체. seller 가 아직 같은 코어 안에 있어 저장소·서비스를 직접 호출한다.
 * seller 의 타입(Seller 엔티티)은 이 파일 밖으로 나가지 않는다.
 */
//cart 에도 같은 이름의 어댑터가 있어 빈 이름을 명시한다(클래스 단순명이 겹치면 기동 실패).
@Component("orderSellerClient")
@RequiredArgsConstructor
public class SellerClient implements SellerPort {

    private final SellerRepository sellerRepository;
    private final CurrentSellerProvider currentSellerProvider;

    /**
     * 로그인한 계정의 승인된 판매자 ID. 신청 이력이 없거나 승인 전이면 빈 값.
     *
     * 빈 값일 때 403 으로 막을지는 order 의 정책이라 여기서 예외를 던지지 않는다.
     */
    @Override
    public Optional<Long> getCurrentSellerId() {
        return currentSellerProvider.getSellerId();
    }

    //판매자가 없으면 빈 값. 주문 조회는 판매자가 지워져도 계속 보여야 하므로 예외로 막지 않는다.
    @Override
    public Optional<SellerInfo> findSeller(Long sellerId) {
        return sellerRepository.findById(sellerId)
                .map(seller -> new SellerInfo(
                        sellerId,
                        seller.getMemberId(),
                        seller.getBakeryName(),
                        seller.getBusinessAddress()
                ));
    }
}
