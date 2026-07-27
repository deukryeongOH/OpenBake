//package com.openbake.settlement.application;
//
//import com.openbake.common.exception.EntityNotFoundException;
//import com.openbake.settlement.domain.SettlementPayout;
//import com.openbake.settlement.domain.SettlementPayoutRepository;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.test.util.ReflectionTestUtils;
//
//import java.math.BigDecimal;
//import java.util.List;
//import java.util.Optional;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.assertj.core.api.Assertions.assertThatThrownBy;
//import static org.mockito.Mockito.verifyNoInteractions;
//import static org.mockito.Mockito.when;
//
//@ExtendWith(MockitoExtension.class)
//class SettlementPayoutQueryServiceTest {
//
//    @Mock
//    private SettlementPayoutRepository payoutRepository;
//
//    private SettlementPayoutQueryService queryService;
//
//    @BeforeEach
//    void setUp() {
//        queryService =
//                new SettlementPayoutQueryService(
//                        payoutRepository
//                );
//    }
//
//    @Test
//    @DisplayName("지급 ID로 지급 이력을 조회한다")
//    void getPayout() {
//        SettlementPayout payout = createPayout(
//                100L,
//                1L,
//                "settlement-1-payout-1"
//        );
//
//        when(payoutRepository.findById(100L))
//                .thenReturn(Optional.of(payout));
//
//        SettlementPayoutResult result =
//                queryService.getPayout(100L);
//
//        assertThat(result.payoutId())
//                .isEqualTo(100L);
//
//        assertThat(result.settlementId())
//                .isEqualTo(1L);
//
//        assertThat(result.status())
//                .isEqualTo("PROCESSING");
//    }
//
//    @Test
//    @DisplayName("정산 ID로 지급 이력 목록을 조회한다")
//    void getPayouts() {
//        SettlementPayout first = createPayout(
//                101L,
//                1L,
//                "settlement-1-payout-2"
//        );
//
//        SettlementPayout second = createPayout(
//                100L,
//                1L,
//                "settlement-1-payout-1"
//        );
//
//        when(payoutRepository.findAllBySettlementId(1L))
//                .thenReturn(List.of(first, second));
//
//        List<SettlementPayoutResult> results =
//                queryService.getPayouts(1L);
//
//        assertThat(results)
//                .hasSize(2);
//
//        assertThat(results.get(0).payoutId())
//                .isEqualTo(101L);
//
//        assertThat(results.get(1).payoutId())
//                .isEqualTo(100L);
//    }
//
//    @Test
//    @DisplayName("존재하지 않는 지급 이력을 조회하면 예외가 발생한다")
//    void rejectMissingPayout() {
//        when(payoutRepository.findById(999L))
//                .thenReturn(Optional.empty());
//
//        assertThatThrownBy(() ->
//                queryService.getPayout(999L)
//        )
//                .isInstanceOf(EntityNotFoundException.class)
//                .hasMessageContaining(
//                        "지급 이력을 찾을 수 없습니다."
//                );
//    }
//
//    @Test
//    @DisplayName("지급 ID는 0보다 커야 한다")
//    void rejectInvalidPayoutId() {
//        assertThatThrownBy(() ->
//                queryService.getPayout(0L)
//        )
//                .isInstanceOf(IllegalArgumentException.class)
//                .hasMessage(
//                        "payoutId는 0보다 커야 합니다."
//                );
//
//        verifyNoInteractions(payoutRepository);
//    }
//
//    private SettlementPayout createPayout(
//            Long payoutId,
//            Long settlementId,
//            String idempotencyKey
//    ) {
//        SettlementPayout payout =
//                SettlementPayout.create(
//                        settlementId,
//                        10L,
//                        new BigDecimal("45000.00"),
//                        idempotencyKey
//                );
//
//        payout.startProcessing();
//
//        ReflectionTestUtils.setField(
//                payout,
//                "id",
//                payoutId
//        );
//
//        return payout;
//    }
//}