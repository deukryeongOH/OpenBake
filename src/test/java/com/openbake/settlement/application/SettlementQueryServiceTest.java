package com.openbake.settlement.application;

import com.openbake.common.exception.EntityNotFoundException;
import com.openbake.settlement.domain.Settlement;
import com.openbake.settlement.domain.SettlementRepository;
import com.openbake.settlement.domain.SettlementStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementQueryServiceTest {

    @Mock
    private SettlementRepository settlementRepository;

    private SettlementQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService =
                new SettlementQueryService(
                        settlementRepository
                );
    }

    @Test
    @DisplayName("정산 ID로 정산서 단건을 조회한다")
    void getSettlement() {
        // given
        Settlement settlement = createSettlement(1L, 10L);

        when(settlementRepository.findById(1L))
                .thenReturn(Optional.of(settlement));

        // when
        SettlementResult result =
                queryService.getSettlement(1L);

        // then
        assertThat(result.settlementId())
                .isEqualTo(1L);

        assertThat(result.sellerId())
                .isEqualTo(10L);

        assertThat(result.grossSalesAmount())
                .isEqualByComparingTo("50000.00");

        assertThat(result.commissionAmount())
                .isEqualByComparingTo("5000.00");

        assertThat(result.payoutAmount())
                .isEqualByComparingTo("45000.00");

        assertThat(result.status())
                .isEqualTo(SettlementStatus.READY.name());
    }

    @Test
    @DisplayName("존재하지 않는 정산을 조회하면 예외가 발생한다")
    void rejectMissingSettlement() {
        // given
        when(settlementRepository.findById(999L))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                queryService.getSettlement(999L)
        )
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(
                        "정산 정보를 찾을 수 없습니다."
                );
    }

    @Test
    @DisplayName("정산 ID는 0보다 커야 한다")
    void rejectInvalidSettlementId() {
        assertThatThrownBy(() ->
                queryService.getSettlement(0L)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "settlementId는 0보다 커야 합니다."
                );

        verifyNoInteractions(settlementRepository);
    }

    private Settlement createSettlement(
            Long settlementId,
            Long sellerId
    ) {
        Settlement settlement = Settlement.create(
                sellerId,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 8, 1),
                new BigDecimal("50000.00"),
                new BigDecimal("5000.00"),
                new BigDecimal("0.00"),
                2
        );

        ReflectionTestUtils.setField(
                settlement,
                "id",
                settlementId
        );

        return settlement;
    }
}
