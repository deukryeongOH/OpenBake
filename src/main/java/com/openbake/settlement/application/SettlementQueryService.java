package com.openbake.settlement.application;

import com.openbake.common.exception.EntityNotFoundException;
import com.openbake.settlement.domain.Settlement;
import com.openbake.settlement.domain.SettlementRepository;
import com.openbake.settlement.domain.SettlementStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementQueryService {

    //목록 페이지 크기 상한. MonthlySettlementBatchQueryService와 동일한 관례.
    private static final int MAX_PAGE_SIZE = 100;

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

    /**
     * 관리자용 정산 목록 검색. 기간/판매자/상태 필터는 모두 선택값이다.
     */
    public SettlementListResult search(
            Long sellerId,
            LocalDate periodStart,
            LocalDate periodEnd,
            SettlementStatus status,
            int page,
            int size
    ) {
        validatePage(page, size);

        List<Settlement> settlements = settlementRepository.search(
                sellerId,
                periodStart,
                periodEnd,
                status,
                page,
                size
        );

        boolean hasNext = settlements.size() > size;

        List<SettlementResult> content = settlements.stream()
                .limit(size)
                .map(SettlementResult::from)
                .toList();

        return new SettlementListResult(content, page, size, hasNext);
    }

    private void validateSettlementId(Long settlementId) {
        if (settlementId == null || settlementId <= 0) {
            throw new IllegalArgumentException(
                    "settlementId는 0보다 커야 합니다."
            );
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "page는 0 이상이어야 합니다."
            );
        }

        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "size는 1 이상 " + MAX_PAGE_SIZE + " 이하여야 합니다."
            );
        }
    }
}
