package com.openbake.settlement.application;

import com.openbake.common.exception.EntityNotFoundException;
import com.openbake.settlement.domain.Settlement;
import com.openbake.settlement.domain.SettlementLineRepository;
import com.openbake.settlement.domain.SettlementPayout;
import com.openbake.settlement.domain.SettlementPayoutRepository;
import com.openbake.settlement.domain.SettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SellerSettlementQueryServiceTest {

    @Mock
    private SettlementRepository settlementRepository;
    @Mock
    private SettlementLineRepository settlementLineRepository;
    @Mock
    private SettlementPayoutRepository settlementPayoutRepository;

    private SellerSettlementQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new SellerSettlementQueryService(
                settlementRepository,
                settlementLineRepository,
                settlementPayoutRepository
        );
    }

    @Test
    @DisplayName("가장 최근 payout이 실패 상태면 실패 사유를 포함한다")
    void getSettlement_includesLatestFailureReason() {
        // given
        Settlement settlement = createSettlement(1L, 10L);

        SettlementPayout failedPayout = createFailedPayout(
                1L,
                10L,
                "계좌 정보 불일치"
        );

        when(settlementRepository.findByIdAndSellerId(1L, 10L))
                .thenReturn(Optional.of(settlement));
        when(settlementLineRepository.findAllBySettlementId(1L))
                .thenReturn(List.of());
        when(settlementPayoutRepository.findAllBySettlementId(1L))
                .thenReturn(List.of(failedPayout));

        // when
        SellerSettlementDetailResult result =
                queryService.getSettlement(10L, 1L);

        // then
        assertThat(result.failureReason())
                .isEqualTo("계좌 정보 불일치");
        assertThat(result.failedAt())
                .isNotNull();
    }

    @Test
    @DisplayName("payout 이력이 없으면 실패 사유는 null이다")
    void getSettlement_withoutPayout() {
        // given
        Settlement settlement = createSettlement(1L, 10L);

        when(settlementRepository.findByIdAndSellerId(1L, 10L))
                .thenReturn(Optional.of(settlement));
        when(settlementLineRepository.findAllBySettlementId(1L))
                .thenReturn(List.of());
        when(settlementPayoutRepository.findAllBySettlementId(1L))
                .thenReturn(List.of());

        // when
        SellerSettlementDetailResult result =
                queryService.getSettlement(10L, 1L);

        // then
        assertThat(result.failureReason()).isNull();
        assertThat(result.failedAt()).isNull();
    }

    @Test
    @DisplayName("존재하지 않는 정산을 조회하면 예외가 발생한다")
    void rejectMissingSettlement() {
        when(settlementRepository.findByIdAndSellerId(999L, 10L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                queryService.getSettlement(10L, 999L)
        ).isInstanceOf(EntityNotFoundException.class);
    }

    private Settlement createSettlement(Long settlementId, Long sellerId) {
        Settlement settlement = Settlement.create(
                sellerId,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 8, 1),
                new BigDecimal("50000.00"),
                new BigDecimal("5000.00"),
                new BigDecimal("0.00"),
                2
        );

        ReflectionTestUtils.setField(settlement, "id", settlementId);

        return settlement;
    }

    private SettlementPayout createFailedPayout(
            Long settlementId,
            Long sellerId,
            String failureReason
    ) {
        SettlementPayout payout = SettlementPayout.create(
                settlementId,
                sellerId,
                new BigDecimal("45000.00"),
                "settlement-1-payout-1",
                "088",
                "1101234567",
                "이세종"
        );

        payout.startProcessing();
        payout.fail(failureReason);

        return payout;
    }
}
