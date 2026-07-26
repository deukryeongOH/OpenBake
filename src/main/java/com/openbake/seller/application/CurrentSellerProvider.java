package com.openbake.seller.application;

import com.openbake.common.security.CurrentMemberProvider;
import com.openbake.seller.domain.ApplicationStatus;
import com.openbake.seller.domain.Seller;
import com.openbake.seller.domain.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CurrentSellerProvider {

    private final CurrentMemberProvider currentMemberProvider;
    private final SellerRepository sellerRepository;

    /**
     * 승인(APPROVED)된 판매자의 sellerId만 반환한다. 신청 이력이 없거나 pending/rejected 상태면 빈 값.
     */
    public Optional<Long> getSellerId() {
        Long memberId = currentMemberProvider.getId();
        return sellerRepository.findByMemberId(memberId)
                .filter(seller -> seller.getApplicationStatus() == ApplicationStatus.APPROVED)
                .map(Seller::getId);
    }
}
