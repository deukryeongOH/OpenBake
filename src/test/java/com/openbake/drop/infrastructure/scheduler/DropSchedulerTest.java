package com.openbake.drop.infrastructure.scheduler;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.drop.application.service.DropEnterService;
import com.openbake.drop.application.service.DropLockService;
import com.openbake.drop.application.service.DropService;
import com.openbake.drop.application.service.DropStockSyncService;
import com.openbake.drop.application.cache.CachedDrop;
import com.openbake.drop.application.cache.TodayDropCache;
import com.openbake.drop.domain.DropStatus;
import com.openbake.drop.domain.EntryStatus;
import com.openbake.drop.domain.entity.Drop;
import com.openbake.drop.domain.entity.DropEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DropSchedulerTest {

    @Mock
    private DropService dropService;

    @Mock
    private DropEnterService dropEnterService;

    @Mock
    private TodayDropCache todayDropCache;

    @Mock
    private DropStockSyncService dropStockSyncService;

    @Mock
    private DropLockService dropLockService;

    @InjectMocks
    private DropScheduler dropScheduler;

    private final Long dropId = 1L;
    private static final Long PRODUCT_ID = 100L;
    private static final int LIMIT_QUANTITY = 5;
    private static final Long MEMBER_ID = 10L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(dropScheduler, "stockFinalizeDelay", Duration.ofMinutes(30));
        ReflectionTestUtils.setField(dropScheduler, "reservationTtl", Duration.ofMinutes(15));
    }

    private static DropEntry reservedEntry(Long dropId, Long memberId) {
        DropEntry entry = DropEntry.builder()
                .dropId(dropId).memberId(memberId).entryStatus(EntryStatus.RESERVED).build();
        return entry;
    }

    // Drop 생성자는 "미래 시각 + 고정 슬롯"만 허용한다. 과거 종료 시각을 가진 드롭을 만들어야 하므로
    // 일단 유효한 값(내일 09:00~10:00)으로 만든 뒤 리플렉션으로 원하는 시각으로 덮어쓴다.
    private static Drop drop(Long dropId, Long productId, LocalDateTime start, LocalDateTime end) {
        LocalDateTime validStart = LocalDate.now().plusDays(1).atTime(9, 0);
        LocalDateTime validEnd = LocalDate.now().plusDays(1).atTime(10, 0);
        Drop drop = Drop.builder()
                .dropStatus(DropStatus.COMPLETED)
                .productId(productId)
                .limitQuantity(LIMIT_QUANTITY)
                .dropStart(validStart)
                .dropEnd(validEnd)
                .build();
        ReflectionTestUtils.setField(drop, "id", dropId);
        ReflectionTestUtils.setField(drop, "dropStart", start);
        ReflectionTestUtils.setField(drop, "dropEnd", end);
        return drop;
    }

    // started/ended가 이미 마킹된 상태로 만들고 싶으면 alreadyStarted/alreadyEnded를 true로 넘긴다
    // (tryMarkStarted/tryMarkEnded가 compareAndSet(false, true)라 이미 true면 항상 false를 반환한다)
    private static CachedDrop cachedDrop(Long dropId, LocalDateTime start, LocalDateTime end, boolean alreadyStarted, boolean alreadyEnded) {
        return new CachedDrop(LocalDate.now(), dropId, PRODUCT_ID, start, end, new AtomicBoolean(alreadyStarted), new AtomicBoolean(alreadyEnded), LIMIT_QUANTITY,
                "두쫀쿠", "설명", "image.jpg", 8000, Set.of(LocalDate.now().plusDays(7)));
    }

    @Test
    @DisplayName("오늘 진행되는 드롭이 없으면 아무 것도 하지 않는다")
    void processDropLifecycle_NoDropToday_DoesNothing() {
        // given
        given(todayDropCache.get()).willReturn(List.of());

        // when
        dropScheduler.processDropLifecycle();

        // then
        verify(dropService, never()).changeDropStatusActive(any());
        verify(dropService, never()).changeDropStatusCompleted(any());
    }

    @Test
    @DisplayName("드롭 시작 전이면 상태 전환 없이 다음 드롭으로 넘어간다")
    void processDropLifecycle_BeforeDropStart_SkipsToNextDrop() {
        // given
        LocalDateTime now = LocalDateTime.now();
        given(todayDropCache.get()).willReturn(List.of(
                cachedDrop(dropId, now.plusMinutes(10), now.plusHours(1), false, false)
        ));

        // when
        dropScheduler.processDropLifecycle();

        // then
        verify(dropService, never()).changeDropStatusActive(any());
        verify(dropStockSyncService, never()).warmUp(any());
    }

    @Test
    @DisplayName("드롭 시작 시각이 지난 첫 tick에는 Active로 전환한다")
    void processDropLifecycle_JustAfterDropStart_ActivatesOnce() {
        // given
        LocalDateTime now = LocalDateTime.now();
        given(todayDropCache.get()).willReturn(List.of(
                cachedDrop(dropId, now.minusMinutes(1), now.plusMinutes(30), false, false)
        ));

        // when
        dropScheduler.processDropLifecycle();

        // then
        verify(dropService).changeDropStatusActive(dropId);
        verify(dropService, never()).changeDropStatusCompleted(any());
    }

    @Test
    @DisplayName("이미 Active 전환된 드롭은 tick마다 다시 전환하지 않는다")
    void processDropLifecycle_AlreadyStarted_DoesNotReactivate() {
        // given
        LocalDateTime now = LocalDateTime.now();
        given(todayDropCache.get()).willReturn(List.of(
                cachedDrop(dropId, now.minusMinutes(1), now.plusMinutes(30), true, false)
        ));

        // when
        dropScheduler.processDropLifecycle();

        // then
        verify(dropService, never()).changeDropStatusActive(any());
    }

    @Test
    @DisplayName("마감 시각이 지난 첫 tick에는 Completed로 전환하고 잔여 진입 내역을 정리한다")
    void processDropLifecycle_AfterDropEnd_CompletesOnceAndExpiresEntries() {
        // given
        LocalDateTime now = LocalDateTime.now();
        given(todayDropCache.get()).willReturn(List.of(
                cachedDrop(dropId, now.minusHours(2), now.minusMinutes(1), true, false)
        ));

        // when
        dropScheduler.processDropLifecycle();

        // then
        verify(dropService).changeDropStatusCompleted(dropId);
        verify(dropEnterService).expireRemainingEntries(dropId);
        verify(dropService, never()).changeDropStatusActive(any());
    }

    @Test
    @DisplayName("이미 Completed 전환된 드롭은 상태 전환도 잔여 정리도 반복하지 않는다")
    void processDropLifecycle_AfterDropEnd_AlreadyCompleted_DoesNotRepeat() {
        // given
        LocalDateTime now = LocalDateTime.now();
        given(todayDropCache.get()).willReturn(List.of(
                cachedDrop(dropId, now.minusHours(2), now.minusMinutes(1), true, true)
        ));

        // when
        dropScheduler.processDropLifecycle();

        // then
        verify(dropService, never()).changeDropStatusCompleted(any());
        verify(dropEnterService, never()).expireRemainingEntries(any());
    }

    @Test
    @DisplayName("여러 드롭이 있을 때 앞쪽 드롭이 시작 전이어도 뒤쪽의 진행 중인 드롭은 정상 처리한다")
    void processDropLifecycle_MultipleDrops_ProcessesEachIndependently() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Long upcomingDropId = 2L;
        Long activeDropId = 3L;
        given(todayDropCache.get()).willReturn(List.of(
                cachedDrop(upcomingDropId, now.plusMinutes(10), now.plusHours(1), false, false),
                cachedDrop(activeDropId, now.minusMinutes(1), now.plusMinutes(30), false, false)
        ));

        // when
        dropScheduler.processDropLifecycle();

        // then
        verify(dropService, never()).changeDropStatusActive(upcomingDropId);
        verify(dropService).changeDropStatusActive(activeDropId);
    }

    @Test
    @DisplayName("드롭 시작 첫 tick에는 Active 전환과 함께 재고 카운터를 워밍업한다")
    void processDropLifecycle_JustAfterDropStart_WarmsUpStock() {
        // given
        LocalDateTime now = LocalDateTime.now();
        CachedDrop drop = cachedDrop(dropId, now.minusMinutes(1), now.plusMinutes(30), false, false);
        given(todayDropCache.get()).willReturn(List.of(drop));

        // when
        dropScheduler.processDropLifecycle();

        // then
        verify(dropStockSyncService).warmUp(drop);
    }

    @Test
    @DisplayName("이미 시작된 드롭은 tick마다 워밍업을 반복하지 않는다")
    void processDropLifecycle_AlreadyStarted_DoesNotWarmUpAgain() {
        // given
        LocalDateTime now = LocalDateTime.now();
        given(todayDropCache.get()).willReturn(List.of(
                cachedDrop(dropId, now.minusMinutes(1), now.plusMinutes(30), true, false)
        ));

        // when
        dropScheduler.processDropLifecycle();

        // then
        verify(dropStockSyncService, never()).warmUp(any());
    }

    /**
     * 재고 확정(finalizeStock)은 더 이상 processDropLifecycle에서 곧바로 일어나지 않는다.
     * 진행 중이던 주문의 만료 처리가 끝날 유예 시간을 준 뒤 finalizeStockAfterGracePeriod가
     * 대신 확정한다 — DropStockSyncService.finalizeStock, DropStockFinalizeThenRollbackBugTest 참고.
     */
    @Test
    @DisplayName("마감 첫 tick은 상태 전환·잔여 정리만 하고 재고 확정은 하지 않는다")
    void processDropLifecycle_AfterDropEnd_DoesNotFinalizeStockImmediately() {
        // given
        LocalDateTime now = LocalDateTime.now();
        CachedDrop drop = cachedDrop(dropId, now.minusHours(2), now.minusMinutes(1), true, false);
        given(todayDropCache.get()).willReturn(List.of(drop));

        // when
        dropScheduler.processDropLifecycle();

        // then
        verify(dropService).changeDropStatusCompleted(dropId);
        verify(dropEnterService).expireRemainingEntries(dropId);
        verify(dropStockSyncService, never()).finalizeStock(any());
    }

    @Test
    @DisplayName("확정 유예 시간이 지난 후보가 있으면 재고를 확정한다")
    void finalizeStockAfterGracePeriod_FinalizesCandidates() {
        // given
        Drop candidate = drop(dropId, PRODUCT_ID,
                LocalDateTime.now().minusHours(2), LocalDateTime.now().minusHours(1));
        given(dropService.findStockFinalizationCandidates(any())).willReturn(List.of(candidate));

        // when
        dropScheduler.finalizeStockAfterGracePeriod();

        // then
        verify(dropStockSyncService).finalizeStock(candidate);
    }

    @Test
    @DisplayName("확정 유예 조회는 마감 시각 기준으로 (지금 - 유예시간)보다 이전인 드롭만 대상으로 한다")
    void finalizeStockAfterGracePeriod_QueriesWithCutoffBeforeDelay() {
        // given
        ReflectionTestUtils.setField(dropScheduler, "stockFinalizeDelay", Duration.ofMinutes(30));
        given(dropService.findStockFinalizationCandidates(any())).willReturn(List.of());

        // when
        LocalDateTime before = LocalDateTime.now();
        dropScheduler.finalizeStockAfterGracePeriod();
        LocalDateTime after = LocalDateTime.now();

        // then: cutoff = 호출 시점의 now - 30분
        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(dropService).findStockFinalizationCandidates(cutoffCaptor.capture());
        assertThat(cutoffCaptor.getValue())
                .isAfterOrEqualTo(before.minusMinutes(30))
                .isBeforeOrEqualTo(after.minusMinutes(30));
    }

    @Test
    @DisplayName("후보가 없으면 아무 것도 하지 않는다")
    void finalizeStockAfterGracePeriod_NoCandidates_DoesNothing() {
        // given
        given(dropService.findStockFinalizationCandidates(any())).willReturn(List.of());

        // when
        dropScheduler.finalizeStockAfterGracePeriod();

        // then
        verify(dropStockSyncService, never()).finalizeStock(any());
    }

    @Test
    @DisplayName("한 드롭의 확정이 실패해도 나머지 드롭은 계속 확정한다")
    void finalizeStockAfterGracePeriod_OneFailureDoesNotStopTheRest() {
        // given
        Drop failing = drop(1L, PRODUCT_ID, LocalDateTime.now().minusHours(2), LocalDateTime.now().minusHours(1));
        Drop succeeding = drop(2L, PRODUCT_ID, LocalDateTime.now().minusHours(2), LocalDateTime.now().minusHours(1));
        given(dropService.findStockFinalizationCandidates(any())).willReturn(List.of(failing, succeeding));
        doThrow(new RuntimeException("일시적 오류")).when(dropStockSyncService).finalizeStock(failing);

        // when
        dropScheduler.finalizeStockAfterGracePeriod();

        // then
        verify(dropStockSyncService).finalizeStock(succeeding);
    }

    @Test
    @DisplayName("진행 중인 드롭만 주기 동기화 대상이다")
    void syncDropStock_OnlySyncsDropsInsideWindow() {
        // given
        LocalDateTime now = LocalDateTime.now();
        CachedDrop upcoming = cachedDrop(2L, now.plusMinutes(10), now.plusHours(1), false, false);
        CachedDrop active = cachedDrop(3L, now.minusMinutes(1), now.plusMinutes(30), true, false);
        CachedDrop ended = cachedDrop(4L, now.minusHours(2), now.minusMinutes(1), true, true);
        given(todayDropCache.get()).willReturn(List.of(upcoming, active, ended));

        // when
        dropScheduler.syncDropStock();

        // then
        verify(dropStockSyncService).sync(active);
        verify(dropStockSyncService, never()).sync(upcoming);
        verify(dropStockSyncService, never()).sync(ended);
    }

    @Test
    @DisplayName("드리프트 검사도 진행 중인 드롭만 대상으로 한다")
    void checkDropStockDrift_OnlyChecksDropsInsideWindow() {
        // given
        LocalDateTime now = LocalDateTime.now();
        CachedDrop upcoming = cachedDrop(2L, now.plusMinutes(10), now.plusHours(1), false, false);
        CachedDrop active = cachedDrop(3L, now.minusMinutes(1), now.plusMinutes(30), true, false);
        CachedDrop ended = cachedDrop(4L, now.minusHours(2), now.minusMinutes(1), true, true);
        given(todayDropCache.get()).willReturn(List.of(upcoming, active, ended));

        // when
        dropScheduler.checkDropStockDrift();

        // then
        verify(dropStockSyncService).checkDrift(active);
        verify(dropStockSyncService, never()).checkDrift(upcoming);
        verify(dropStockSyncService, never()).checkDrift(ended);
    }

    @Test
    @DisplayName("주기 동기화는 드리프트 검사를 함께 수행하지 않는다 (주기가 분리됐다)")
    void syncDropStock_DoesNotCheckDrift() {
        // given
        LocalDateTime now = LocalDateTime.now();
        CachedDrop active = cachedDrop(dropId, now.minusMinutes(1), now.plusMinutes(30), true, false);
        given(todayDropCache.get()).willReturn(List.of(active));

        // when
        dropScheduler.syncDropStock();

        // then
        verify(dropStockSyncService).sync(active);
        verify(dropStockSyncService, never()).checkDrift(any());
    }

    @Test
    @DisplayName("서버 기동 시 오늘 드롭 캐시를 갱신한다")
    void init_RefreshesTodayDropCache() {
        // when
        dropScheduler.init();

        // then
        verify(todayDropCache).refresh();
    }

    @Test
    @DisplayName("자정 스케줄에서 오늘 드롭 캐시를 갱신한다")
    void refreshTodayDrop_RefreshesTodayDropCache() {
        // when
        dropScheduler.refreshTodayDrop();

        // then
        verify(todayDropCache).refresh();
    }

    @Test
    @DisplayName("방치된 선점 후보가 있으면 회수한다")
    void sweepAbandonedReservations_RollsBackCandidates() {
        // given
        DropEntry entry = reservedEntry(dropId, MEMBER_ID);
        given(dropLockService.findExpiredReservations(any())).willReturn(List.of(entry));

        // when
        dropScheduler.sweepAbandonedReservations();

        // then
        verify(dropLockService).rollbackStock(dropId, MEMBER_ID);
    }

    @Test
    @DisplayName("후보가 없으면 아무 것도 하지 않는다")
    void sweepAbandonedReservations_NoCandidates_DoesNothing() {
        // given
        given(dropLockService.findExpiredReservations(any())).willReturn(List.of());

        // when
        dropScheduler.sweepAbandonedReservations();

        // then
        verify(dropLockService, never()).rollbackStock(any(), any());
    }

    @Test
    @DisplayName("조회 시점 이후 경합으로 이미 처리된 항목은(NOT_RESERVED_STATUS) 조용히 넘어간다")
    void sweepAbandonedReservations_AlreadyHandled_SkipsQuietly() {
        // given: 조회와 회수 사이에 결제가 완료됐거나 다른 인스턴스가 먼저 회수한 경우
        DropEntry entry = reservedEntry(dropId, MEMBER_ID);
        given(dropLockService.findExpiredReservations(any())).willReturn(List.of(entry));
        doThrow(new BusinessException(ErrorCode.NOT_RESERVED_STATUS))
                .when(dropLockService).rollbackStock(dropId, MEMBER_ID);

        // when & then (예외가 스케줄러 밖으로 전파되지 않는다)
        dropScheduler.sweepAbandonedReservations();
    }

    @Test
    @DisplayName("한 건 회수가 실패해도 나머지 후보는 계속 처리한다")
    void sweepAbandonedReservations_OneFailureDoesNotStopTheRest() {
        // given
        DropEntry failing = reservedEntry(1L, MEMBER_ID);
        DropEntry succeeding = reservedEntry(2L, MEMBER_ID);
        given(dropLockService.findExpiredReservations(any())).willReturn(List.of(failing, succeeding));
        doThrow(new RuntimeException("일시적 오류")).when(dropLockService).rollbackStock(1L, MEMBER_ID);

        // when
        dropScheduler.sweepAbandonedReservations();

        // then
        verify(dropLockService).rollbackStock(2L, MEMBER_ID);
    }

    @Test
    @DisplayName("확정 유예 조회는 (지금 - reservationTtl)보다 이전에 선점된 것만 대상으로 한다")
    void sweepAbandonedReservations_QueriesWithCutoffBeforeTtl() {
        // given
        given(dropLockService.findExpiredReservations(any())).willReturn(List.of());

        // when
        LocalDateTime before = LocalDateTime.now();
        dropScheduler.sweepAbandonedReservations();
        LocalDateTime after = LocalDateTime.now();

        // then: cutoff = 호출 시점의 now - 15분
        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(dropLockService).findExpiredReservations(cutoffCaptor.capture());
        assertThat(cutoffCaptor.getValue())
                .isAfterOrEqualTo(before.minusMinutes(15))
                .isBeforeOrEqualTo(after.minusMinutes(15));
    }
}