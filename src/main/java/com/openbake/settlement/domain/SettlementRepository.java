package com.openbake.settlement.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SettlementRepository {

    Settlement save(Settlement settlement);

    Optional<Settlement> findById(Long id);

    Optional<Settlement> findBySellerIdAndPeriodStartAndPeriodEnd(
            Long sellerId,
            LocalDate periodStart,
            LocalDate periodEnd
    );
    /** 중복 조회는 Spring Batch를 재실행했을 때
     * 같은 판매자와 기간의 정산서가
     * 다시 생성되는 것을 방지하는 데 사용
     **/
    boolean existsBySellerIdAndPeriodStartAndPeriodEnd(
            Long sellerId,
            LocalDate periodStart,
            LocalDate periodEnd
    );

    List<Settlement> findAllBySellerId(
            Long sellerId
    );

    Optional<Settlement> findByIdAndSellerId(
            Long settlementId,
            Long sellerId
    );

    /**
     * 관리자용 정산 목록 검색. 각 필터는 null이면 조건에서 제외한다.
     * 다음 페이지 존재 여부 판정을 위해 size + 1건을 조회해서 반환한다
     * (MonthlySettlementBatchQueryService.getExecutions와 동일한 관례).
     */
    List<Settlement> search(
            Long sellerId,
            LocalDate periodStart,
            LocalDate periodEnd,
            SettlementStatus status,
            int page,
            int size
    );
}