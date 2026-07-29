package com.openbake.settlement.application;

import com.openbake.common.exception.EntityNotFoundException;
import com.openbake.settlement.domain.Settlement;
import com.openbake.settlement.domain.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementQueryService {

    private final SettlementRepository settlementRepository;

    public SettlementResult getSettlement(Long settlementId) {
        validateSettlementId(settlementId);

        Settlement settlement =
                settlementRepository.findById(settlementId)
                        .orElseThrow(() ->
                                new EntityNotFoundException(
                                        "정산 정보를 찾을 수 없습니다. "
                                                + "settlementId="
                                                + settlementId
                                )
                        );

        return SettlementResult.from(settlement);
    }

    private void validateSettlementId(Long settlementId) {
        if (settlementId == null || settlementId <= 0) {
            throw new IllegalArgumentException(
                    "settlementId는 0보다 커야 합니다."
            );
        }
    }
}
