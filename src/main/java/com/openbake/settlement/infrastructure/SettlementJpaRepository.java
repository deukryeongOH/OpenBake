package com.openbake.settlement.infrastructure;

import com.openbake.settlement.domain.Settlement;
import com.openbake.settlement.domain.SettlementStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SettlementJpaRepository
        extends JpaRepository<Settlement, Long> {

    Optional<Settlement> findBySellerIdAndPeriodStartAndPeriodEnd(
            Long sellerId,
            LocalDate periodStart,
            LocalDate periodEnd
    );

    boolean existsBySellerIdAndPeriodStartAndPeriodEnd(
            Long sellerId,
            LocalDate periodStart,
            LocalDate periodEnd
    );

    /**
     * 정산 목록은 최신 정산 기간부터 보이도록 정렬
     * periodStart DESC
     * id DESC
     * */
    List<Settlement> findAllBySellerIdOrderByPeriodStartDescIdDesc(
            Long sellerId
    );

    Optional<Settlement> findByIdAndSellerId(
            Long settlementId,
            Long sellerId
    );

    /**
     * 관리자용 정산 목록 검색. 파라미터가 null이면 해당 조건은 무시한다.
     */
    @Query("""
            SELECT s FROM Settlement s
            WHERE (:sellerId IS NULL OR s.sellerId = :sellerId)
            AND (:periodStart IS NULL OR s.periodStart >= :periodStart)
            AND (:periodEnd IS NULL OR s.periodEnd <= :periodEnd)
            AND (:status IS NULL OR s.status = :status)
            ORDER BY s.periodStart DESC, s.id DESC
            """)
    List<Settlement> search(
            @Param("sellerId") Long sellerId,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd,
            @Param("status") SettlementStatus status,
            Pageable pageable
    );
}