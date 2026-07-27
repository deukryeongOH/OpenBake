//package com.openbake.settlement.application;
//
//import com.openbake.common.exception.EntityNotFoundException;
//import com.openbake.settlement.domain.Settlement;
//import com.openbake.settlement.domain.SettlementPayout;
//import com.openbake.settlement.domain.SettlementPayoutRepository;
//import com.openbake.settlement.domain.SettlementPayoutStatus;
//import com.openbake.settlement.domain.SettlementRepository;
//import com.openbake.settlement.domain.SettlementStatus;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.test.util.ReflectionTestUtils;
//
//import java.math.BigDecimal;
//import java.time.LocalDate;
//import java.util.Optional;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.assertj.core.api.Assertions.assertThatThrownBy;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.Mockito.never;
//import static org.mockito.Mockito.verify;
//import static org.mockito.Mockito.verifyNoInteractions;
//import static org.mockito.Mockito.when;
//
//@ExtendWith(MockitoExtension.class)
//class SettlementPayoutServiceTest {
//
//    @Mock
//    private SettlementRepository settlementRepository;
//
//    @Mock
//    private SettlementPayoutRepository payoutRepository;
//
//    private SettlementPayoutService payoutService;
//
//    @BeforeEach
//    void setUp() {
//        payoutService = new SettlementPayoutService(
//                settlementRepository,
//                payoutRepository
//        );
//    }
//
//    @Test
//    @DisplayName("정산 지급을 시작하고 지급 원장을 저장한다")
//    void start() {
//        // given
//        Settlement settlement = createSettlement(1L);
//
//        when(payoutRepository.findByIdempotencyKey(
//                "settlement-1-payout-1"
//        )).thenReturn(Optional.empty());
//
//        when(settlementRepository.findById(1L))
//                .thenReturn(Optional.of(settlement));
//
//        when(payoutRepository.save(any(SettlementPayout.class)))
//                .thenAnswer(invocation -> {
//                    SettlementPayout payout =
//                            invocation.getArgument(0);
//
//                    ReflectionTestUtils.setField(
//                            payout,
//                            "id",
//                            100L
//                    );
//
//                    return payout;
//                });
//
//        // when
//        SettlementPayoutResult result =
//                payoutService.start(
//                        1L,
//                        "settlement-1-payout-1"
//                );
//
//        // then
//        assertThat(result.payoutId())
//                .isEqualTo(100L);
//
//        assertThat(result.settlementId())
//                .isEqualTo(1L);
//
//        assertThat(result.sellerId())
//                .isEqualTo(10L);
//
//        assertThat(result.payoutAmount())
//                .isEqualByComparingTo("45000.00");
//
//        assertThat(result.idempotencyKey())
//                .isEqualTo("settlement-1-payout-1");
//
//        assertThat(result.status())
//                .isEqualTo(
//                        SettlementPayoutStatus.PROCESSING.name()
//                );
//
//        assertThat(settlement.getStatus())
//                .isEqualTo(SettlementStatus.PAYING);
//
//        verify(payoutRepository)
//                .save(any(SettlementPayout.class));
//    }
//
//    @Test
//    @DisplayName("같은 멱등키 요청은 기존 지급 결과를 반환한다")
//    void returnExistingPayoutForSameIdempotencyKey() {
//        // given
//        SettlementPayout existingPayout =
//                createProcessingPayout(
//                        100L,
//                        1L,
//                        "settlement-1-payout-1"
//                );
//
//        when(payoutRepository.findByIdempotencyKey(
//                "settlement-1-payout-1"
//        )).thenReturn(Optional.of(existingPayout));
//
//        // when
//        SettlementPayoutResult result =
//                payoutService.start(
//                        1L,
//                        "settlement-1-payout-1"
//                );
//
//        // then
//        assertThat(result.payoutId())
//                .isEqualTo(100L);
//
//        assertThat(result.status())
//                .isEqualTo("PROCESSING");
//
//        verify(payoutRepository, never())
//                .save(any(SettlementPayout.class));
//
//        verifyNoInteractions(settlementRepository);
//    }
//
//    @Test
//    @DisplayName("다른 정산에서 사용된 멱등키는 사용할 수 없다")
//    void rejectIdempotencyKeyUsedByAnotherSettlement() {
//        // given
//        SettlementPayout existingPayout =
//                createProcessingPayout(
//                        100L,
//                        2L,
//                        "duplicated-key"
//                );
//
//        when(payoutRepository.findByIdempotencyKey(
//                "duplicated-key"
//        )).thenReturn(Optional.of(existingPayout));
//
//        // when & then
//        assertThatThrownBy(() ->
//                payoutService.start(
//                        1L,
//                        "duplicated-key"
//                )
//        )
//                .isInstanceOf(IllegalStateException.class)
//                .hasMessage(
//                        "다른 정산에서 이미 사용된 멱등키입니다."
//                );
//
//        verify(payoutRepository, never())
//                .save(any(SettlementPayout.class));
//
//        verifyNoInteractions(settlementRepository);
//    }
//
//    @Test
//    @DisplayName("지급 성공 시 지급 원장과 정산을 모두 완료 처리한다")
//    void complete() {
//        // given
//        Settlement settlement = createSettlement(1L);
//        settlement.startPaying();
//
//        SettlementPayout payout =
//                createProcessingPayout(
//                        100L,
//                        1L,
//                        "settlement-1-payout-1"
//                );
//
//        when(payoutRepository.findById(100L))
//                .thenReturn(Optional.of(payout));
//
//        when(settlementRepository.findById(1L))
//                .thenReturn(Optional.of(settlement));
//
//        // when
//        SettlementPayoutResult result =
//                payoutService.complete(
//                        100L,
//                        "bank-transfer-20260723-0001"
//                );
//
//        // then
//        assertThat(result.status())
//                .isEqualTo(
//                        SettlementPayoutStatus.COMPLETED.name()
//                );
//
//        assertThat(result.externalTransactionId())
//                .isEqualTo(
//                        "bank-transfer-20260723-0001"
//                );
//
//        assertThat(result.completedAt())
//                .isNotNull();
//
//        assertThat(settlement.getStatus())
//                .isEqualTo(SettlementStatus.COMPLETED);
//    }
//
//    @Test
//    @DisplayName("지급 실패 시 지급 원장과 정산을 모두 실패 처리한다")
//    void fail() {
//        // given
//        Settlement settlement = createSettlement(1L);
//        settlement.startPaying();
//
//        SettlementPayout payout =
//                createProcessingPayout(
//                        100L,
//                        1L,
//                        "settlement-1-payout-1"
//                );
//
//        when(payoutRepository.findById(100L))
//                .thenReturn(Optional.of(payout));
//
//        when(settlementRepository.findById(1L))
//                .thenReturn(Optional.of(settlement));
//
//        // when
//        SettlementPayoutResult result =
//                payoutService.fail(
//                        100L,
//                        "판매자 계좌 정보 오류"
//                );
//
//        // then
//        assertThat(result.status())
//                .isEqualTo(
//                        SettlementPayoutStatus.FAILED.name()
//                );
//
//        assertThat(result.failureReason())
//                .isEqualTo("판매자 계좌 정보 오류");
//
//        assertThat(result.failedAt())
//                .isNotNull();
//
//        assertThat(settlement.getStatus())
//                .isEqualTo(SettlementStatus.FAILED);
//    }
//
//    @Test
//    @DisplayName("존재하지 않는 정산은 지급을 시작할 수 없다")
//    void rejectMissingSettlement() {
//        // given
//        when(payoutRepository.findByIdempotencyKey(
//                "settlement-999-payout-1"
//        )).thenReturn(Optional.empty());
//
//        when(settlementRepository.findById(999L))
//                .thenReturn(Optional.empty());
//
//        // when & then
//        assertThatThrownBy(() ->
//                payoutService.start(
//                        999L,
//                        "settlement-999-payout-1"
//                )
//        )
//                .isInstanceOf(EntityNotFoundException.class)
//                .hasMessageContaining(
//                        "정산 정보를 찾을 수 없습니다."
//                );
//
//        verify(payoutRepository, never())
//                .save(any(SettlementPayout.class));
//    }
//
//    @Test
//    @DisplayName("존재하지 않는 지급 원장은 완료 처리할 수 없다")
//    void rejectMissingPayout() {
//        // given
//        when(payoutRepository.findById(999L))
//                .thenReturn(Optional.empty());
//
//        // when & then
//        assertThatThrownBy(() ->
//                payoutService.complete(
//                        999L,
//                        "bank-transfer-1"
//                )
//        )
//                .isInstanceOf(EntityNotFoundException.class)
//                .hasMessageContaining(
//                        "지급 이력을 찾을 수 없습니다."
//                );
//
//        verifyNoInteractions(settlementRepository);
//    }
//
//    @Test
//    @DisplayName("정산 ID는 0보다 커야 한다")
//    void rejectInvalidSettlementId() {
//        assertThatThrownBy(() ->
//                payoutService.start(
//                        0L,
//                        "settlement-0-payout-1"
//                )
//        )
//                .isInstanceOf(IllegalArgumentException.class)
//                .hasMessage(
//                        "settlementId는 0보다 커야 합니다."
//                );
//
//        verifyNoInteractions(
//                settlementRepository,
//                payoutRepository
//        );
//    }
//
//    @Test
//    @DisplayName("지급 ID는 0보다 커야 한다")
//    void rejectInvalidPayoutId() {
//        assertThatThrownBy(() ->
//                payoutService.complete(
//                        0L,
//                        "bank-transfer-1"
//                )
//        )
//                .isInstanceOf(IllegalArgumentException.class)
//                .hasMessage(
//                        "payoutId는 0보다 커야 합니다."
//                );
//
//        verifyNoInteractions(
//                settlementRepository,
//                payoutRepository
//        );
//    }
//
//    private Settlement createSettlement(Long settlementId) {
//        Settlement settlement = Settlement.create(
//                10L,
//                LocalDate.of(2026, 7, 1),
//                LocalDate.of(2026, 8, 1),
//                new BigDecimal("50000.00"),
//                new BigDecimal("5000.00"),
//                new BigDecimal("0.00"),
//                2
//        );
//
//        ReflectionTestUtils.setField(
//                settlement,
//                "id",
//                settlementId
//        );
//
//        return settlement;
//    }
//
//    private SettlementPayout createProcessingPayout(
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
//
//    @Test
//    @DisplayName("멱등키는 필수이다")
//    void rejectBlankIdempotencyKey() {
//        assertThatThrownBy(() ->
//                payoutService.start(1L, " ")
//        )
//                .isInstanceOf(IllegalArgumentException.class)
//                .hasMessage("멱등키는 필수입니다.");
//
//        verifyNoInteractions(
//                settlementRepository,
//                payoutRepository
//        );
//    }
//}