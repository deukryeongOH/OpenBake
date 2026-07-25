package com.openbake.drop.application.event;

import com.openbake.drop.application.dto.PaymentCompletedEvent;
import com.openbake.drop.application.dto.PaymentFailedEvent;
import com.openbake.drop.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DropEventListenerTest {

    @Mock
    private DropInventoryRepository dropInventoryRepository;

    @Mock
    private DropEntryRepository dropEntryRepository;

    @InjectMocks
    private DropEventListener dropEventListener;

    private final Long dropId = 1L;
    private final Long memberId = 10L;

    private DropEntry reservedEntry;
    private DropInventory dropInventory;

    @BeforeEach
    void setUp() {
        reservedEntry = DropEntry.builder()
                .dropId(dropId)
                .memberId(memberId)
                .entryStatus(EntryStatus.RESERVED)
                .build();

        dropInventory = DropInventory.builder()
                .dropId(dropId)
                .totalQuantity(100)
                .remainQuantity(90) // 10개가 이미 선점된 상태를 가정
                .build();
    }

    @Test
    @DisplayName("결제 완료 이벤트 처리 성공 - DropEntry 상태가 COMPLETED로 변경된다")
    void handlePaymentCompleted_Success() {
        // given
        given(dropEntryRepository.findByDropIdAndMemberId(dropId, memberId))
                .willReturn(Optional.of(reservedEntry));

        PaymentCompletedEvent event = new PaymentCompletedEvent(dropId, memberId, 100L);

        // when
        dropEventListener.handlePaymentCompleted(event);

        // then
        assertThat(reservedEntry.getEntryStatus()).isEqualTo(EntryStatus.COMPLETED);
    }

    @Test
    @DisplayName("결제 실패 이벤트 처리 성공 - DropEntry 상태가 FAILED로 변경되고 재고가 복구된다")
    void handlePaymentFailed_Success() {
        // given
        given(dropEntryRepository.findByDropIdAndMemberId(dropId, memberId))
                .willReturn(Optional.of(reservedEntry));
        given(dropInventoryRepository.findByDropId(dropId)).willReturn(dropInventory);

        PaymentFailedEvent event = new PaymentFailedEvent(dropId, memberId, 10);

        // when
        dropEventListener.handlePaymentFailed(event);

        // then
        assertThat(reservedEntry.getEntryStatus()).isEqualTo(EntryStatus.FAILED);
        assertThat(dropInventory.getRemainQuantity()).isEqualTo(100);
    }
}