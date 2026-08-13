package com.openbake.drop.application;

import com.openbake.common.exception.BusinessException;
import com.openbake.drop.application.dto.DropProductInfoResult;
import com.openbake.drop.application.dto.DropReserveCommand;
import com.openbake.drop.application.port.CurrentMemberPort;
import com.openbake.drop.application.port.ProductPort;
import com.openbake.drop.application.queue.QueueManager;
import com.openbake.drop.application.service.DropLockService;
import com.openbake.drop.application.service.DropService;
import com.openbake.drop.domain.*;
import com.openbake.drop.domain.entity.Drop;
import com.openbake.drop.domain.entity.DropEntry;
import com.openbake.drop.domain.repository.DropEntryRepository;
import com.openbake.drop.domain.repository.DropRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DropLockServiceTest {

    @Mock
    private DropRepository dropRepository;

    @Mock
    private DropService dropService;

    @Mock
    private DropEntryRepository dropEntryRepository;

    @Mock
    private QueueManager queueManager;

    @Mock
    private ProductPort productPort;

    @Mock
    private CurrentMemberPort currentMemberPort;

    @InjectMocks
    private DropLockService dropLockService;

    private final Long dropId = 1L;
    private final Long memberId = 10L;
    private final Long productId = 500L;

    private DropEntry enteredEntry;
    private Drop drop;

    @BeforeEach
    void setUp() {
        enteredEntry = DropEntry.builder()
                .dropId(dropId)
                .memberId(memberId)
                .entryStatus(EntryStatus.ENTERED)
                .build();

        drop = Drop.builder()
                .dropStatus(DropStatus.ACTIVE)
                .productId(productId)
                .limitQuantity(5)
                .dropStart(LocalDateTime.of(2028, 7, 25, 9, 0))
                .dropEnd(LocalDateTime.of(2028, 7, 25, 10, 0))
                .build();
        ReflectionTestUtils.setField(drop, "id", dropId);
    }

    private DropProductInfoResult productInfo(int remainQuantity) {
        return DropProductInfoResult.of(
                "두쫀쿠", "원물 맛이 많이 나요.", "image.jpg",
                Set.of(LocalDate.of(2028, 7, 28)), 8000, 100, remainQuantity, 1L, productId);
    }

    @Test
    @DisplayName("재고 차감 성공 - DropEntry가 원자적으로 RESERVED로 전환되고 재고가 차감된다")
    void decreaseQuantity_Success() {
        // given
        given(dropRepository.findById(dropId)).willReturn(Optional.of(drop));
        given(dropEntryRepository.reserve(dropId, memberId, 3)).willReturn(1);
        given(productPort.decreaseQuantity(productId, 3)).willReturn(97);

        // when
        dropLockService.decreaseQuantity(dropId, memberId, 3);

        // then
        verify(dropEntryRepository).reserve(dropId, memberId, 3);
        verify(productPort).decreaseQuantity(productId, 3);
        verify(dropService, never()).changeDropStatusCompleted(any());
        verify(queueManager, never()).markSoldOut(any());
    }

    @Test
    @DisplayName("ENTERED 상태가 아니면(이미 예약됐거나 대기열 통과 상태가 아니면) 재고를 건드리지 않고 예외를 던진다")
    void decreaseQuantity_Fail_NotEnteredStatus_DoesNotTouchStock() {
        // given
        given(dropRepository.findById(dropId)).willReturn(Optional.of(drop));
        given(dropEntryRepository.reserve(dropId, memberId, 3)).willReturn(0);

        // when & then
        assertThatThrownBy(() -> dropLockService.decreaseQuantity(dropId, memberId, 3))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(productPort);
    }

    @Test
    @DisplayName("차감 후 남은 수량이 0이면 드롭을 COMPLETED로 바꾸고 대기열에 품절 마킹한다")
    void decreaseQuantity_SoldOut_MarksDropCompleted() {
        // given
        given(dropRepository.findById(dropId)).willReturn(Optional.of(drop));
        given(dropEntryRepository.reserve(dropId, memberId, 5)).willReturn(1);
        given(productPort.decreaseQuantity(productId, 5)).willReturn(0);

        // when
        dropLockService.decreaseQuantity(dropId, memberId, 5);

        // then
        verify(dropService).changeDropStatusCompleted(dropId);
        verify(queueManager).markSoldOut(dropId);
    }

    @Test
    @DisplayName("재고 선점 성공 - 사전 검증을 통과하면 재고를 차감한다")
    void reserveStock_Success() {
        // given
        given(currentMemberPort.getCurrentMemberId()).willReturn(memberId);
        given(dropRepository.findById(dropId)).willReturn(Optional.of(drop));
        given(productPort.getProductInfo(productId)).willReturn(productInfo(100));
        given(dropEntryRepository.reserve(dropId, memberId, 3)).willReturn(1);
        given(productPort.decreaseQuantity(productId, 3)).willReturn(97);

        // when
        dropLockService.reserveStock(dropId, DropReserveCommand.create(3));

        // then
        verify(dropEntryRepository).reserve(dropId, memberId, 3);
        verify(productPort).decreaseQuantity(productId, 3);
    }

    @Test
    @DisplayName("1인당 제한 수량을 초과하면 재고 차감 시도 없이 예외를 던진다")
    void reserveStock_Fail_ExceedsLimitPerPerson() {
        // given: drop.limitQuantity == 5
        given(currentMemberPort.getCurrentMemberId()).willReturn(memberId);
        given(dropRepository.findById(dropId)).willReturn(Optional.of(drop));

        // when & then
        assertThatThrownBy(() -> dropLockService.reserveStock(dropId, DropReserveCommand.create(10)))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(dropEntryRepository);
        verify(productPort, never()).decreaseQuantity(any(), anyInt());
    }

    @Test
    @DisplayName("대기열 통과 상태 검증(레거시 - Facade 전용) 성공")
    void checkEntryStatus_Success() {
        // given
        given(dropEntryRepository.findByDropIdAndMemberId(dropId, memberId))
                .willReturn(Optional.of(enteredEntry));

        // when & then (예외 없이 통과)
        dropLockService.checkEntryStatus(dropId, memberId);
    }

    @Test
    @DisplayName("1인당 제한 수량 검증 성공")
    void checkLimitQuantityPerPerson_Success() {
        // given
        given(dropRepository.findById(dropId)).willReturn(Optional.of(drop));

        // when & then (예외 없이 통과)
        dropLockService.checkLimitQuantityPerPerson(dropId, 3);
    }

    @Test
    @DisplayName("남은 수량 검증 성공")
    void checkSelectQuantity_Success() {
        // given
        given(dropRepository.findById(dropId)).willReturn(Optional.of(drop));
        given(productPort.getProductInfo(productId)).willReturn(productInfo(100));

        // when & then (예외 없이 통과)
        dropLockService.checkSelectQuantity(dropId, 3);
    }

    @Test
    @DisplayName("재고 롤백 성공 - DropEntry가 원자적으로 FAILED로 전환되고 재고가 복구된다")
    void rollbackStock_Success() {
        // given
        ReflectionTestUtils.setField(enteredEntry, "selectQuantity", 5);
        given(dropEntryRepository.findByDropIdAndMemberId(dropId, memberId))
                .willReturn(Optional.of(enteredEntry));
        given(dropRepository.findById(dropId)).willReturn(Optional.of(drop));
        given(dropEntryRepository.fail(dropId, memberId)).willReturn(1);
        given(productPort.rollbackQuantity(productId, 5)).willReturn(5);

        // when
        dropLockService.rollbackStock(dropId, memberId);

        // then
        verify(dropEntryRepository).fail(dropId, memberId);
        verify(productPort).rollbackQuantity(productId, 5);
    }

    @Test
    @DisplayName("이미 롤백됐거나 RESERVED 상태가 아니면 재고를 건드리지 않고 예외를 던진다")
    void rollbackStock_Fail_NotReservedStatus_DoesNotTouchStock() {
        // given
        given(dropEntryRepository.findByDropIdAndMemberId(dropId, memberId))
                .willReturn(Optional.of(enteredEntry));
        given(dropRepository.findById(dropId)).willReturn(Optional.of(drop));
        given(dropEntryRepository.fail(dropId, memberId)).willReturn(0);

        // when & then
        assertThatThrownBy(() -> dropLockService.rollbackStock(dropId, memberId))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(productPort);
    }

    @Test
    @DisplayName("품절로 COMPLETED된 드롭이 마감 전에 재고가 복구되면 ACTIVE로 되돌리고 soldOut을 해제한다")
    void rollbackStock_Revives_CompletedDropWhenWithinWindowAndStockAvailable() {
        // given
        Drop soldOutDrop = Drop.builder()
                .dropStatus(DropStatus.ACTIVE) // COMPLETED로 강제 변경 후 사용
                .productId(productId)
                .limitQuantity(5)
                // 빌더는 과거 시작 시각/TimeSlot 외 시각을 허용하지 않으므로 미래의 슬롯 시각으로 생성.
                // 아직 마감 전임을 보장하기 위해 dropEnd도 충분히 먼 미래로 둔다.
                .dropStart(LocalDateTime.of(2028, 7, 25, 9, 0))
                .dropEnd(LocalDateTime.of(2028, 7, 25, 10, 0))
                .build();
        ReflectionTestUtils.setField(soldOutDrop, "id", dropId);
        soldOutDrop.changeStatus(DropStatus.COMPLETED); // 품절로 인해 COMPLETED된 상태 재현

        ReflectionTestUtils.setField(enteredEntry, "selectQuantity", 5);
        given(dropEntryRepository.findByDropIdAndMemberId(dropId, memberId))
                .willReturn(Optional.of(enteredEntry));
        given(dropRepository.findById(dropId)).willReturn(Optional.of(soldOutDrop));
        given(dropEntryRepository.fail(dropId, memberId)).willReturn(1);
        given(productPort.rollbackQuantity(productId, 5)).willReturn(5);

        // when
        dropLockService.rollbackStock(dropId, memberId);

        // then
        verify(dropService).changeDropStatusActive(dropId);
        verify(queueManager).unmarkSoldOut(dropId);
    }

    @Test
    @DisplayName("마감 시각이 지나 COMPLETED된 드롭은 재고가 복구돼도 ACTIVE로 되돌리지 않는다")
    void rollbackStock_DoesNotRevive_WhenDropWindowAlreadyEnded() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Drop endedDrop = Drop.builder()
                .dropStatus(DropStatus.ACTIVE)
                .productId(productId)
                .limitQuantity(5)
                // builder 검증(과거 불가, TimeSlot 정렬) 통과용, 아래서 시작/종료 시각을 과거로 되돌림
                .dropStart(LocalDateTime.of(2028, 7, 25, 9, 0))
                .dropEnd(LocalDateTime.of(2028, 7, 25, 10, 0))
                .build();
        ReflectionTestUtils.setField(endedDrop, "id", dropId);
        // 드롭 진행 시간은 항상 1시간이므로 dropStart/dropEnd를 함께 과거로 되돌린다
        ReflectionTestUtils.setField(endedDrop, "dropStart", now.minusHours(1).minusMinutes(1));
        ReflectionTestUtils.setField(endedDrop, "dropEnd", now.minusMinutes(1)); // 마감 시각을 과거로 조정
        endedDrop.changeStatus(DropStatus.COMPLETED);

        ReflectionTestUtils.setField(enteredEntry, "selectQuantity", 5);
        given(dropEntryRepository.findByDropIdAndMemberId(dropId, memberId))
                .willReturn(Optional.of(enteredEntry));
        given(dropRepository.findById(dropId)).willReturn(Optional.of(endedDrop));
        given(dropEntryRepository.fail(dropId, memberId)).willReturn(1);
        given(productPort.rollbackQuantity(productId, 5)).willReturn(5);

        // when
        dropLockService.rollbackStock(dropId, memberId);

        // then
        verify(dropService, never()).changeDropStatusActive(any());
        verify(queueManager, never()).unmarkSoldOut(any());
    }

    @Test
    @DisplayName("COMPLETED가 아닌(정상 진행 중인) 드롭은 재고 롤백만 하고 상태 전환은 시도하지 않는다")
    void rollbackStock_DoesNotRevive_WhenDropIsNotCompleted() {
        // given
        ReflectionTestUtils.setField(enteredEntry, "selectQuantity", 3);
        given(dropEntryRepository.findByDropIdAndMemberId(dropId, memberId))
                .willReturn(Optional.of(enteredEntry));
        given(dropRepository.findById(dropId)).willReturn(Optional.of(drop)); // status = ACTIVE
        given(dropEntryRepository.fail(dropId, memberId)).willReturn(1);
        given(productPort.rollbackQuantity(productId, 3)).willReturn(100);

        // when
        dropLockService.rollbackStock(dropId, memberId);

        // then
        verify(dropService, never()).changeDropStatusActive(any());
        verify(queueManager, never()).unmarkSoldOut(any());
    }
}