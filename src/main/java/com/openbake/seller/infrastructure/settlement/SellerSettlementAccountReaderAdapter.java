package com.openbake.seller.infrastructure.settlement;

import com.openbake.common.exception.EntityNotFoundException;
import com.openbake.seller.domain.ApplicationStatus;
import com.openbake.seller.domain.Seller;
import com.openbake.seller.domain.SellerRepository;
import com.openbake.settlement.application.port
        .SellerSettlementAccountReader;
import com.openbake.settlement.application.port
        .SellerSettlementAccountSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerSettlementAccountReaderAdapter
        implements SellerSettlementAccountReader {

    private final SellerRepository sellerRepository;

    @Override
    public SellerSettlementAccountSnapshot getAccount(
            Long sellerId
    ) {
        Seller seller =
                sellerRepository.findById(sellerId)
                        .orElseThrow(() ->
                                new EntityNotFoundException(
                                        "판매자 정보를 찾을 수 없습니다."
                                )
                        );

        if (seller.getApplicationStatus()
                != ApplicationStatus.APPROVED) {
            throw new IllegalStateException(
                    "승인된 판매자만 정산금을 지급받을 수 있습니다."
            );
        }

        if (!seller.isAccountVerified()) {
            throw new IllegalStateException(
                    "인증된 정산 계좌가 없습니다."
            );
        }

        return new SellerSettlementAccountSnapshot(
                seller.getId(),
                seller.getSettlementBankCode(),
                seller.getSettlementAccountNumber(),
                seller.getSettlementAccountHolder()
        );
    }
}