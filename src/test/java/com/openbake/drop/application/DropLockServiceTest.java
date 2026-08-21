package com.openbake.drop.application;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.drop.application.cache.CachedDrop;
import com.openbake.drop.application.cache.TodayDropCache;
import com.openbake.drop.application.dto.DropProductInfoResult;
import com.openbake.drop.application.dto.DropReserveCommand;
import com.openbake.drop.application.port.CurrentMemberPort;
import com.openbake.drop.application.port.ProductPort;
import com.openbake.drop.application.port.StockReservationPort;
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
import java.util.concurrent.atomic.AtomicBoolean;

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
    private ProductPort productPort;

    @Mock
    private CurrentMemberPort currentMemberPort;

    @Mock
    private StockReservationPort stockReservationPort;

    @Mock
    private TodayDropCache todayDropCache;

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

    // TodayDropCache가 돌려주는 오늘 드롭. limitQuantity/productId는 시작 후 불변이라 캐시에서 읽는다.
    private CachedDrop cachedDrop(LocalDateTime dropEnd) {
        return new CachedDrop(LocalDate.now(), dropId, productId,
                LocalDateTime.now().minusMinutes(10), dropEnd,
                new AtomicBoolean(true), new AtomicBoolean(false), 5,
                "두쫀쿠", "설명", "image.jpg", 8000, Set.of(LocalDate.now().plusDays(7)));
    }

    private void givenDropIsCachedAndRunning() {
        given(todayDropCache.find(dropId))
                .willReturn(Optional.of(cachedDrop(LocalDateTime.now().plusMinutes(30))));
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
        given(dropEntryRepository.reserve(dropId, memberId, 3)).willReturn(1);
        given(stockReservationPort.reserve(dropId, 3)).willReturn(97L);

        // when
        dropLockService.decreaseQuantity(dropId, memberId, 3);

        // then
        verify(dropEntryRepository).reserve(dropId, memberId, 3);
        verify(stockReservationPort).reserve(dropId, 3);
        // 재고 차감이 Redis 로 옮겨갔으므로 DB 단일 row UPDATE 는 더 이상 호출되지 않는다
        verify(productPort, never()).decreaseQuantity(any(), anyInt());
        verify(dropService, never()).changeDropStatusCompleted(any());
    }

    @Test
    @DisplayName("ENTERED 상태가 아니면(이미 예약됐거나 입장하지 않았으면) 재고를 건드리지 않고 예외를 던진다")
    void decreaseQuantity_Fail_NotEnteredStatus_DoesNotTouchStock() {
        // given
        given(dropEntryRepository.reserve(dropId, memberId, 3)).willReturn(0);

        // when & then
        assertThatThrownBy(() -> dropLockService.decreaseQuantity(dropId, memberId, 3))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(productPort);
        verifyNoInteractions(stockReservationPort);
    }

    @Test
    @DisplayName("차감 후 남은 수량이 0이면 드롭을 COMPLETED로 바꾼다")
    void decreaseQuantity_SoldOut_MarksDropCompleted() {
        // given
        given(dropEntryRepository.reserve(dropId, memberId, 5)).willReturn(1);
        given(stockReservationPort.reserve(dropId, 5)).willReturn(0L);

        // when
        dropLockService.decreaseQuantity(dropId, memberId, 5);

        // then
        verify(dropService).changeDropStatusCompleted(dropId);
    }

    @Test
    @DisplayName("재고 카운터가 초기화되지 않았으면 선점을 거부한다(fail-closed)")
    void decreaseQuantity_StockNotInitialized_Rejects() {
        // given
        given(dropEntryRepository.reserve(dropId, memberId, 3)).willReturn(1);
        given(stockReservationPort.reserve(dropId, 3)).willReturn((long) StockReservationPort.NOT_INITIALIZED);

        // when & then
        assertThatThrownBy(() -> dropLockService.decreaseQuantity(dropId, memberId, 3))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.STOCK_NOT_INITIALIZED.getMessage());

        verify(dropService, never()).changeDropStatusCompleted(any());
    }

    @Test
    @DisplayName("재고가 부족하면 품절 예외를 던진다")
    void decreaseQuantity_OutOfStock_Throws() {
        // given
        given(dropEntryRepository.reserve(dropId, memberId, 3)).willReturn(1);
        given(stockReservationPort.reserve(dropId, 3)).willReturn((long) StockReservationPort.OUT_OF_STOCK);

        // when & then
        assertThatThrownBy(() -> dropLockService.decreaseQuantity(dropId, memberId, 3))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.DROP_OUT_OF_STOCK.getMessage());

    }

    @Test
    @DisplayName("재고 선점 성공 - 사전 검증을 통과하면 재고를 차감한다")
    void reserveStock_Success() {
        // given
        given(currentMemberPort.getCurrentMemberId()).willReturn(memberId);
        givenDropIsCachedAndRunning();
        given(dropEntryRepository.reserve(dropId, memberId, 3)).willReturn(1);
        given(stockReservationPort.reserve(dropId, 3)).willReturn(97L);

        // when
        dropLockService.reserveStock(dropId, DropReserveCommand.create(3));

        // then
        verify(dropEntryRepository).reserve(dropId, memberId, 3);
        verify(stockReservationPort).reserve(dropId, 3);
    }

    @Test
    @DisplayName("1인당 제한 수량을 초과하면 재고 차감 시도 없이 예외를 던진다")
    void reserveStock_Fail_ExceedsLimitPerPerson() {
        // given: 캐시된 drop.limitQuantity == 5
        given(currentMemberPort.getCurrentMemberId()).willReturn(memberId);
        givenDropIsCachedAndRunning();

        // when & then
        assertThatThrownBy(() -> dropLockService.reserveStock(dropId, DropReserveCommand.create(10)))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(dropEntryRepository);
        verifyNoInteractions(stockReservationPort);
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
        givenDropIsCachedAndRunning();

        // when & then (예외 없이 통과)
        dropLockService.checkLimitQuantityPerPerson(dropId, 3);
    }

    @Test
    @DisplayName("남은 수량 검증 성공")
    void checkSelectQuantity_Success() {
        // given
        givenDropIsCachedAndRunning();
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
        givenDropIsCachedAndRunning();
        given(dropEntryRepository.fail(dropId, memberId)).willReturn(1);
        given(productPort.getTotalQuantity(productId)).willReturn(100);
        given(stockReservationPort.rollback(dropId, 5, 100)).willReturn(5L);

        // when
        dropLockService.rollbackStock(dropId, memberId);

        // then
        verify(dropEntryRepository).fail(dropId, memberId);
        verify(stockReservationPort).rollback(dropId, 5, 100);
        verify(productPort, never()).rollbackQuantity(any(), anyInt());
    }

    @Test
    @DisplayName("이미 롤백됐거나 RESERVED 상태가 아니면 재고를 건드리지 않고 예외를 던진다")
    void rollbackStock_Fail_NotReservedStatus_DoesNotTouchStock() {
        // given
        given(dropEntryRepository.findByDropIdAndMemberId(dropId, memberId))
                .willReturn(Optional.of(enteredEntry));
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
        ReflectionTestUtils.setField(soldOutDrop, "dropStatus", DropStatus.COMPLETED); // 품절로 인해 COMPLETED된 상태 재현

        ReflectionTestUtils.setField(enteredEntry, "selectQuantity", 5);
        given(dropEntryRepository.findByDropIdAndMemberId(dropId, memberId))
                .willReturn(Optional.of(enteredEntry));
        givenDropIsCachedAndRunning();
        given(dropEntryRepository.fail(dropId, memberId)).willReturn(1);
        given(productPort.getTotalQuantity(productId)).willReturn(100);
        given(stockReservationPort.rollback(dropId, 5, 100)).willReturn(5L);

        // when
        dropLockService.rollbackStock(dropId, memberId);

        // then
        verify(dropService).reviveFromSoldOut(dropId);
        // 상태를 읽지 않고 조건부 UPDATE로 처리하므로 drops 조회가 없다
        verify(dropRepository, never()).findById(any());
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
        ReflectionTestUtils.setField(endedDrop, "dropStatus", DropStatus.COMPLETED);

        ReflectionTestUtils.setField(enteredEntry, "selectQuantity", 5);
        given(dropEntryRepository.findByDropIdAndMemberId(dropId, memberId))
                .willReturn(Optional.of(enteredEntry));
        given(todayDropCache.find(dropId))
                .willReturn(Optional.of(cachedDrop(now.minusMinutes(1))));
        given(dropEntryRepository.fail(dropId, memberId)).willReturn(1);
        given(productPort.getTotalQuantity(productId)).willReturn(100);
        given(stockReservationPort.rollback(dropId, 5, 100)).willReturn(5L);

        // when
        dropLockService.rollbackStock(dropId, memberId);

        // then
        verify(dropService, never()).reviveFromSoldOut(any());
        // 마감 창 밖이라 상태 확인용 DB 조회 자체가 일어나지 않는다
        verify(dropRepository, never()).findById(any());
    }

    @Test
    @DisplayName("마감 전에 재고가 복구되면 상태를 읽지 않고 복구 UPDATE를 호출한다 (COMPLETED 여부는 UPDATE의 WHERE가 판단)")
    void rollbackStock_WithinWindow_DelegatesReviveToConditionalUpdate() {
        // given
        ReflectionTestUtils.setField(enteredEntry, "selectQuantity", 3);
        given(dropEntryRepository.findByDropIdAndMemberId(dropId, memberId))
                .willReturn(Optional.of(enteredEntry));
        givenDropIsCachedAndRunning();
        given(dropEntryRepository.fail(dropId, memberId)).willReturn(1);
        given(productPort.getTotalQuantity(productId)).willReturn(100);
        given(stockReservationPort.rollback(dropId, 3, 100)).willReturn(100L);

        // when
        dropLockService.rollbackStock(dropId, memberId);

        // then
        verify(dropService).reviveFromSoldOut(dropId);
        verify(dropRepository, never()).findById(any());
    }

    @Test
    @DisplayName("오늘 진행되는 드롭이 아니면(캐시 미스) 재고 선점을 DB 조회 없이 거부한다(fail-closed)")
    void checkLimitQuantityPerPerson_CacheMiss_RejectsWithoutDbLookup() {
        // given
        given(todayDropCache.find(dropId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> dropLockService.checkLimitQuantityPerPerson(dropId, 3))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DROP_NOT_ACTIVE);

        verifyNoInteractions(dropRepository);
    }

    @Test
    @DisplayName("드롭 당일이 지나 캐시에 없어도 롤백은 DB 폴백으로 정상 처리된다")
    void rollbackStock_CacheMiss_FallsBackToDb() {
        // given
        ReflectionTestUtils.setField(enteredEntry, "selectQuantity", 5);
        given(dropEntryRepository.findByDropIdAndMemberId(dropId, memberId))
                .willReturn(Optional.of(enteredEntry));
        given(todayDropCache.find(dropId)).willReturn(Optional.empty());
        given(dropRepository.findById(dropId)).willReturn(Optional.of(drop));
        given(dropEntryRepository.fail(dropId, memberId)).willReturn(1);
        given(productPort.getTotalQuantity(productId)).willReturn(100);
        given(stockReservationPort.rollback(dropId, 5, 100)).willReturn(5L);

        // when
        dropLockService.rollbackStock(dropId, memberId);

        // then
        verify(stockReservationPort).rollback(dropId, 5, 100);
        // 캐시 미스 = 진행 창 밖이므로 되살리지 않는다
        verify(dropService, never()).reviveFromSoldOut(any());
    }
}