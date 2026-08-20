package com.openbake.settlement.application;

import com.openbake.settlement.domain.Settlement;
import com.openbake.settlement.domain.SettlementLine;
import com.openbake.settlement.domain.SettlementLineRepository;
import com.openbake.settlement.domain.SettlementRepository;
import com.openbake.settlement.domain.SettlementTarget;
import com.openbake.settlement.domain.SettlementTargetRepository;
import com.openbake.settlement.domain.SettlementTargetStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 현재 서비스는 같은 기간을 다시 실행했을 때 PENDING 대상이 없다면 빈 결과를 반환
 * 또한 아래 비정상 상태를 감지
 * 동일 판매자·기간 Settlement 존재
 * +
 * 해당 기간의 PENDING Target 존재
 * 이 경우 일부 대상이 정산에서 빠지는 것을 막기 위해 예외를 발생
 */
@ExtendWith(MockitoExtension.class)
class MonthlySettlementServiceTest {

    private static final LocalDate PERIOD_START =
            LocalDate.of(2026, 7, 1);

    private static final LocalDate PERIOD_END =
            LocalDate.of(2026, 8, 1);

    @Mock
    private SettlementTargetRepository settlementTargetRepository;

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private SettlementLineRepository settlementLineRepository;

    private MonthlySettlementService monthlySettlementService;

    @BeforeEach
    void setUp() {
        monthlySettlementService = new MonthlySettlementService(
                settlementTargetRepository,
                settlementRepository,
                settlementLineRepository
        );
    }

    @Test
    @DisplayName("정산 대상이 없으면 빈 처리 결과를 반환한다")
    void returnEmptyResultWhenNoPendingTargets() {
        // given
        when(settlementTargetRepository.findAllPendingTargets(
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)
        )).thenReturn(List.of());

        // when
        MonthlySettlementResult result =
                monthlySettlementService.settle(
                        PERIOD_START,
                        PERIOD_END
                );

        // then
        assertThat(result.periodStart())
                .isEqualTo(PERIOD_START);

        assertThat(result.periodEnd())
                .isEqualTo(PERIOD_END);

        assertThat(result.settlementCount())
                .isZero();

        assertThat(result.targetCount())
                .isZero();

        assertThat(result.totalPayoutAmount())
                .isEqualByComparingTo("0.00");

        verify(settlementRepository, never())
                .save(any(Settlement.class));

        verify(settlementLineRepository, never())
                .saveAll(any());

        verify(settlementTargetRepository, never())
                .saveAll(any());
    }

    @Test
    @DisplayName("판매자별로 정산서를 생성하고 정산 대상을 배정한다")
    void createSettlementsGroupedBySeller() {
        // given
        SettlementTarget seller10Target1 = createSavedTarget(
                1L,
                "event-001",
                1001L,
                2001L,
                10L,
                3001L,
                "제주 당근 케이크",
                2,
                "30000.00"
        );

        SettlementTarget seller10Target2 = createSavedTarget(
                2L,
                "event-002",
                1002L,
                2002L,
                10L,
                3002L,
                "제주 감귤 타르트",
                1,
                "20000.00"
        );

        SettlementTarget seller20Target1 = createSavedTarget(
                3L,
                "event-003",
                1003L,
                2003L,
                20L,
                3003L,
                "우도 땅콩 쿠키",
                3,
                "10000.00"
        );

        when(settlementTargetRepository.findAllPendingTargets(
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)
        )).thenReturn(List.of(
                seller10Target1,
                seller10Target2,
                seller20Target1
        ));

        when(settlementRepository
                .existsBySellerIdAndPeriodStartAndPeriodEnd(
                        any(Long.class),
                        any(LocalDate.class),
                        any(LocalDate.class)
                ))
                .thenReturn(false);

        AtomicLong settlementSequence =
                new AtomicLong(100L);

        when(settlementRepository.save(any(Settlement.class)))
                .thenAnswer(invocation -> {
                    Settlement settlement =
                            invocation.getArgument(0);

                    ReflectionTestUtils.setField(
                            settlement,
                            "id",
                            settlementSequence.getAndIncrement()
                    );

                    return settlement;
                });

        when(settlementLineRepository.saveAll(any()))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        when(settlementTargetRepository.saveAll(any()))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        // when
        MonthlySettlementResult result =
                monthlySettlementService.settle(
                        PERIOD_START,
                        PERIOD_END
                );

        // then
        assertThat(result.periodStart())
                .isEqualTo(PERIOD_START);

        assertThat(result.periodEnd())
                .isEqualTo(PERIOD_END);

        assertThat(result.settlementCount())
                .isEqualTo(2);

        assertThat(result.targetCount())
                .isEqualTo(3);

        /*
         * 판매자 10:
         * 총액 50,000원
         * 수수료 5,000원
         * 지급액 45,000원
         *
         * 판매자 20:
         * 총액 10,000원
         * 수수료 1,000원
         * 지급액 9,000원
         *
         * 총 지급액 54,000원
         */
        assertThat(result.totalPayoutAmount())
                .isEqualByComparingTo("54000.00");

        assertThat(seller10Target1.getStatus())
                .isEqualTo(SettlementTargetStatus.ASSIGNED);

        assertThat(seller10Target2.getStatus())
                .isEqualTo(SettlementTargetStatus.ASSIGNED);

        assertThat(seller20Target1.getStatus())
                .isEqualTo(SettlementTargetStatus.ASSIGNED);

        assertThat(seller10Target1.getSettlementId())
                .isEqualTo(100L);

        assertThat(seller10Target2.getSettlementId())
                .isEqualTo(100L);

        assertThat(seller20Target1.getSettlementId())
                .isEqualTo(101L);

        verify(settlementRepository)
                .existsBySellerIdAndPeriodStartAndPeriodEnd(
                        10L,
                        PERIOD_START,
                        PERIOD_END
                );

        verify(settlementRepository)
                .existsBySellerIdAndPeriodStartAndPeriodEnd(
                        20L,
                        PERIOD_START,
                        PERIOD_END
                );
    }

    @Test
    @DisplayName("판매자별 총 판매금액과 수수료를 합산한다")
    void aggregateSettlementAmounts() {
        // given
        SettlementTarget target1 = createSavedTarget(
                1L,
                "event-001",
                1001L,
                2001L,
                10L,
                3001L,
                "상품 1",
                2,
                "30000.00"
        );

        SettlementTarget target2 = createSavedTarget(
                2L,
                "event-002",
                1002L,
                2002L,
                10L,
                3002L,
                "상품 2",
                1,
                "20000.00"
        );

        when(settlementTargetRepository.findAllPendingTargets(
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)
        )).thenReturn(List.of(target1, target2));

        when(settlementRepository
                .existsBySellerIdAndPeriodStartAndPeriodEnd(
                        10L,
                        PERIOD_START,
                        PERIOD_END
                ))
                .thenReturn(false);

        when(settlementRepository.save(any(Settlement.class)))
                .thenAnswer(invocation -> {
                    Settlement settlement =
                            invocation.getArgument(0);

                    ReflectionTestUtils.setField(
                            settlement,
                            "id",
                            100L
                    );

                    return settlement;
                });

        when(settlementLineRepository.saveAll(any()))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        when(settlementTargetRepository.saveAll(any()))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        ArgumentCaptor<Settlement> settlementCaptor =
                ArgumentCaptor.forClass(Settlement.class);

        // when
        monthlySettlementService.settle(
                PERIOD_START,
                PERIOD_END
        );

        // then
        verify(settlementRepository)
                .save(settlementCaptor.capture());

        Settlement settlement =
                settlementCaptor.getValue();

        assertThat(settlement.getSellerId())
                .isEqualTo(10L);

        assertThat(settlement.getGrossSalesAmount())
                .isEqualByComparingTo("50000.00");

        assertThat(settlement.getCommissionAmount())
                .isEqualByComparingTo("5000.00");

        assertThat(settlement.getNetSalesAmount())
                .isEqualByComparingTo("45000.00");

        assertThat(settlement.getAdjustmentAmount())
                .isEqualByComparingTo("0.00");

        assertThat(settlement.getPayoutAmount())
                .isEqualByComparingTo("45000.00");

        assertThat(settlement.getTargetCount())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("SettlementLine에는 Target의 스냅샷이 저장된다")
    @SuppressWarnings("unchecked")
    void createSettlementLinesFromTargets() {
        // given
        SettlementTarget target = createSavedTarget(
                1L,
                "event-001",
                1001L,
                2001L,
                10L,
                3001L,
                "제주 당근 케이크",
                2,
                "30000.00"
        );

        when(settlementTargetRepository.findAllPendingTargets(
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)
        )).thenReturn(List.of(target));

        when(settlementRepository
                .existsBySellerIdAndPeriodStartAndPeriodEnd(
                        10L,
                        PERIOD_START,
                        PERIOD_END
                ))
                .thenReturn(false);

        when(settlementRepository.save(any(Settlement.class)))
                .thenAnswer(invocation -> {
                    Settlement settlement =
                            invocation.getArgument(0);

                    ReflectionTestUtils.setField(
                            settlement,
                            "id",
                            100L
                    );

                    return settlement;
                });

        when(settlementLineRepository.saveAll(any()))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        when(settlementTargetRepository.saveAll(any()))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        ArgumentCaptor<List<SettlementLine>> lineCaptor =
                ArgumentCaptor.forClass(List.class);

        // when
        monthlySettlementService.settle(
                PERIOD_START,
                PERIOD_END
        );

        // then
        verify(settlementLineRepository)
                .saveAll(lineCaptor.capture());

        List<SettlementLine> savedLines =
                lineCaptor.getValue();

        assertThat(savedLines)
                .hasSize(1);

        SettlementLine line =
                savedLines.get(0);

        assertThat(line.getSettlementId())
                .isEqualTo(100L);

        assertThat(line.getTargetId())
                .isEqualTo(1L);

        assertThat(line.getOrderId())
                .isEqualTo(1001L);

        assertThat(line.getOrderItemId())
                .isEqualTo(2001L);

        assertThat(line.getProductId())
                .isEqualTo(3001L);

        assertThat(line.getProductNameSnapshot())
                .isEqualTo("제주 당근 케이크");

        assertThat(line.getQuantity())
                .isEqualTo(2);

        assertThat(line.getGrossAmount())
                .isEqualByComparingTo("30000.00");

        assertThat(line.getCommissionAmount())
                .isEqualByComparingTo("3000.00");

        assertThat(line.getNetAmount())
                .isEqualByComparingTo("27000.00");
    }

    @Test
    @DisplayName("동일 판매자와 기간의 정산서가 이미 있으면 예외가 발생한다")
    void rejectDuplicateSettlement() {
        // given
        SettlementTarget target = createSavedTarget(
                1L,
                "event-001",
                1001L,
                2001L,
                10L,
                3001L,
                "제주 당근 케이크",
                2,
                "30000.00"
        );

        when(settlementTargetRepository.findAllPendingTargets(
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)
        )).thenReturn(List.of(target));

        when(settlementRepository
                .existsBySellerIdAndPeriodStartAndPeriodEnd(
                        10L,
                        PERIOD_START,
                        PERIOD_END
                ))
                .thenReturn(true);

        // when & then
        assertThatThrownBy(() ->
                monthlySettlementService.settle(
                        PERIOD_START,
                        PERIOD_END
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(
                        "동일 판매자와 정산 기간의 정산서가 이미 존재합니다."
                )
                .hasMessageContaining("sellerId=10");

        verify(settlementRepository, never())
                .save(any(Settlement.class));

        verify(settlementLineRepository, never())
                .saveAll(any());

        verify(settlementTargetRepository, never())
                .saveAll(any());

        assertThat(target.getStatus())
                .isEqualTo(SettlementTargetStatus.PENDING);

        assertThat(target.getSettlementId())
                .isNull();
    }

    @Test
    @DisplayName("정산 시작일과 종료일이 같으면 예외가 발생한다")
    void rejectSameStartAndEndDate() {
        assertThatThrownBy(() ->
                monthlySettlementService.settle(
                        PERIOD_START,
                        PERIOD_START
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "periodStart는 periodEnd보다 이전이어야 합니다."
                );

        verify(settlementTargetRepository, never())
                .findAllPendingTargets(
                        any(OffsetDateTime.class),
                        any(OffsetDateTime.class)
                );
    }

    @Test
    @DisplayName("정산 시작일이 종료일보다 늦으면 예외가 발생한다")
    void rejectStartDateAfterEndDate() {
        assertThatThrownBy(() ->
                monthlySettlementService.settle(
                        PERIOD_END,
                        PERIOD_START
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "periodStart는 periodEnd보다 이전이어야 합니다."
                );
    }

    private SettlementTarget createSavedTarget(
            Long targetId,
            String eventId,
            Long orderId,
            Long orderItemId,
            Long sellerId,
            Long productId,
            String productName,
            Integer quantity,
            String grossAmount
    ) {
        SettlementTarget target =
                SettlementTarget.create(
                        eventId,
                        orderId,
                        orderItemId,
                        sellerId,
                        productId,
                        productName,
                        quantity,
                        new BigDecimal(grossAmount),
                        new BigDecimal("0.1000"),
                        OffsetDateTime.parse(
                                "2026-07-15T10:00:00+09:00"
                        )
                );

        ReflectionTestUtils.setField(
                target,
                "id",
                targetId
        );

        return target;
    }
}