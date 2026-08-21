package com.openbake.settlement.application;

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
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementEventServiceTest {

    private static final String EVENT_ID =
            "c41f55a8-9246-4bd6-bdf7-87b109fdb0c1";

    private static final String DIFFERENT_EVENT_ID =
            "9c42040a-6a87-42ef-b2d8-59d644d581f3";

    private static final Long ORDER_ID = 1001L;
    private static final Long ORDER_ITEM_ID = 2001L;
    private static final Long SELLER_ID = 10L;
    private static final Long PRODUCT_ID = 3001L;

    private static final OffsetDateTime PURCHASE_CONFIRMED_AT =
            OffsetDateTime.parse("2026-07-21T10:00:00+09:00");

    @Mock
    private SettlementInboxEventRepository settlementInboxRepository;

    @Mock
    private SettlementTargetRepository settlementTargetRepository;

    private SettlementEventService settlementEventService;

    @BeforeEach
    void setUp() {
        settlementEventService = new SettlementEventService(
                settlementInboxRepository,
                settlementTargetRepository
        );
    }

    @Test
    @DisplayName("새 구매확정 이벤트를 받으면 정산 대상과 Inbox를 저장한다")
    void receiveNewPurchaseConfirmedEvent() {
        // given
        ReceivePurchaseConfirmedCommand command = createCommand(EVENT_ID);

        when(settlementInboxRepository.existsByEventId(EVENT_ID))
                .thenReturn(false);

        when(settlementTargetRepository.findByOrderIdAndOrderItemId(
                ORDER_ID,
                ORDER_ITEM_ID
        )).thenReturn(Optional.empty());

        when(settlementTargetRepository.save(any(SettlementTarget.class)))
                .thenAnswer(invocation -> {
                    SettlementTarget target = invocation.getArgument(0);

                    // JPA 저장 후 ID가 생성된 상황을 단위 테스트에서 흉내 냅니다.
                    ReflectionTestUtils.setField(target, "id", 1L);

                    return target;
                });

        // when
        SettlementEventResult result =
                settlementEventService.receive(command);

        // then
        assertThat(result.eventId()).isEqualTo(EVENT_ID);
        assertThat(result.settlementTargetId()).isEqualTo(1L);
        assertThat(result.duplicate()).isFalse();

        ArgumentCaptor<SettlementTarget> targetCaptor =
                ArgumentCaptor.forClass(SettlementTarget.class);

        verify(settlementTargetRepository)
                .save(targetCaptor.capture());

        SettlementTarget savedTarget = targetCaptor.getValue();

        assertThat(savedTarget.getSourceEventId())
                .isEqualTo(EVENT_ID);

        assertThat(savedTarget.getOrderId())
                .isEqualTo(ORDER_ID);

        assertThat(savedTarget.getOrderItemId())
                .isEqualTo(ORDER_ITEM_ID);

        assertThat(savedTarget.getSellerId())
                .isEqualTo(SELLER_ID);

        assertThat(savedTarget.getProductId())
                .isEqualTo(PRODUCT_ID);

        assertThat(savedTarget.getProductNameSnapshot())
                .isEqualTo("제주 당근 케이크");

        assertThat(savedTarget.getQuantity())
                .isEqualTo(2);

        assertThat(savedTarget.getGrossAmount())
                .isEqualByComparingTo("30000.00");

        assertThat(savedTarget.getCommissionRateSnapshot())
                .isEqualByComparingTo("0.1000");

        assertThat(savedTarget.getCommissionAmount())
                .isEqualByComparingTo("3000.00");

        assertThat(savedTarget.getNetAmount())
                .isEqualByComparingTo("27000.00");

        assertThat(savedTarget.getPurchaseConfirmedAt())
                .isEqualTo(PURCHASE_CONFIRMED_AT);

        assertThat(savedTarget.getSettlementId())
                .isNull();

        assertThat(savedTarget.getStatus())
                .isEqualTo(SettlementTargetStatus.PENDING);

        assertThat(savedTarget.getCreatedAt())
                .isNotNull();

        verify(settlementInboxRepository).save(
                EVENT_ID,
                "PURCHASE_CONFIRMED"
        );
    }

    @Test
    @DisplayName("동일 eventId가 다시 전달되면 정산 대상을 중복 생성하지 않는다")
    void doNotCreateTargetWhenEventIdIsDuplicated() {
        // given
        ReceivePurchaseConfirmedCommand command = createCommand(EVENT_ID);
        SettlementTarget existingTarget = createExistingTarget(EVENT_ID, 1L);

        when(settlementInboxRepository.existsByEventId(EVENT_ID))
                .thenReturn(true);

        when(settlementTargetRepository.findByOrderIdAndOrderItemId(
                ORDER_ID,
                ORDER_ITEM_ID
        )).thenReturn(Optional.of(existingTarget));

        // when
        SettlementEventResult result =
                settlementEventService.receive(command);

        // then
        assertThat(result.eventId()).isEqualTo(EVENT_ID);
        assertThat(result.settlementTargetId()).isEqualTo(1L);
        assertThat(result.duplicate()).isTrue();

        verify(settlementTargetRepository, never())
                .save(any(SettlementTarget.class));

        verify(settlementInboxRepository, never())
                .save(anyString(), anyString());
    }

    @Test
    @DisplayName("eventId가 달라도 동일 주문 항목이면 정산 대상을 중복 생성하지 않는다")
    void doNotCreateTargetWhenOrderItemAlreadyExists() {
        // given
        ReceivePurchaseConfirmedCommand command =
                createCommand(DIFFERENT_EVENT_ID);

        SettlementTarget existingTarget =
                createExistingTarget(EVENT_ID, 1L);

        when(settlementInboxRepository.existsByEventId(DIFFERENT_EVENT_ID))
                .thenReturn(false);

        when(settlementTargetRepository.findByOrderIdAndOrderItemId(
                ORDER_ID,
                ORDER_ITEM_ID
        )).thenReturn(Optional.of(existingTarget));

        // when
        SettlementEventResult result =
                settlementEventService.receive(command);

        // then
        assertThat(result.eventId())
                .isEqualTo(DIFFERENT_EVENT_ID);

        assertThat(result.settlementTargetId())
                .isEqualTo(1L);

        assertThat(result.duplicate())
                .isTrue();

        verify(settlementTargetRepository, never())
                .save(any(SettlementTarget.class));

        /*
         * 새로운 eventId 자체는 정상적으로 소비한 것이므로
         * Inbox에는 처리 이력을 남깁니다.
         */
        verify(settlementInboxRepository).save(
                DIFFERENT_EVENT_ID,
                "PURCHASE_CONFIRMED"
        );
    }

    private ReceivePurchaseConfirmedCommand createCommand(
            String eventId
    ) {
        return new ReceivePurchaseConfirmedCommand(
                eventId,
                ORDER_ID,
                ORDER_ITEM_ID,
                SELLER_ID,
                PRODUCT_ID,
                "제주 당근 케이크",
                2,
                new BigDecimal("30000.00"),
                PURCHASE_CONFIRMED_AT
        );
    }

    private SettlementTarget createExistingTarget(
            String eventId,
            Long targetId
    ) {
        SettlementTarget target = SettlementTarget.create(
                eventId,
                ORDER_ID,
                ORDER_ITEM_ID,
                SELLER_ID,
                PRODUCT_ID,
                "제주 당근 케이크",
                2,
                new BigDecimal("30000.00"),
                new BigDecimal("0.1000"),
                PURCHASE_CONFIRMED_AT
        );

        ReflectionTestUtils.setField(target, "id", targetId);

        return target;
    }
}