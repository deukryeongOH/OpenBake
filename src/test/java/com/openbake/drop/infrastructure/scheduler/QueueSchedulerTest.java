package com.openbake.drop.infrastructure.scheduler;

import com.openbake.drop.application.service.DropEnterService;
import com.openbake.drop.application.service.DropService;
import com.openbake.drop.application.service.DropStockSyncService;
import com.openbake.drop.application.queue.InMemoryQueueManager;
import com.openbake.drop.application.cache.CachedDrop;
import com.openbake.drop.application.cache.TodayDropCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QueueSchedulerTest {

    @Mock
    private InMemoryQueueManager queueManager;

    @Mock
    private DropService dropService;

    @Mock
    private DropEnterService dropEnterService;

    @Mock
    private TodayDropCache todayDropCache;

    @Mock
    private DropStockSyncService dropStockSyncService;

    @InjectMocks
    private QueueScheduler queueScheduler;

    private final Long dropId = 1L;
    private static final Long PRODUCT_ID = 100L;

    // started/ended가 이미 마킹된 상태로 만들고 싶으면 alreadyStarted/alreadyEnded를 true로 넘긴다
    // (tryMarkStarted/tryMarkEnded가 compareAndSet(false, true)라 이미 true면 항상 false를 반환한다)
    private static CachedDrop cachedDrop(Long dropId, LocalDateTime start, LocalDateTime end, boolean alreadyStarted, boolean alreadyEnded) {
        return new CachedDrop(LocalDate.now(), dropId, PRODUCT_ID, start, end, new AtomicBoolean(alreadyStarted), new AtomicBoolean(alreadyEnded));
    }

    @Test
    @DisplayName("오늘 진행되는 드롭이 없으면 아무 것도 하지 않는다")
    void processQueue_NoDropToday_DoesNothing() {
        // given
        given(todayDropCache.get()).willReturn(List.of());

        // when
        queueScheduler.processQueue();

        // then
        verify(dropService, never()).changeDropStatusActive(any());
        verify(dropService, never()).changeDropStatusCompleted(any());
        verify(queueManager, never()).allowEntries(any(), anyInt());
        verify(queueManager, never()).finishDrop(any());
    }

    @Test
    @DisplayName("드롭 시작 전이면 상태 전환/대기열 정리 없이 다음 드롭으로 넘어간다")
    void processQueue_BeforeDropStart_SkipsToNextDrop() {
        // given
        LocalDateTime now = LocalDateTime.now();
        given(todayDropCache.get()).willReturn(List.of(
                cachedDrop(dropId, now.plusMinutes(10), now.plusHours(1), false, false)
        ));

        // when
        queueScheduler.processQueue();

        // then
        verify(queueManager, never()).finishDrop(any());
        verify(queueManager, never()).allowEntries(any(), anyInt());
        verify(dropService, never()).changeDropStatusActive(any());
    }

    @Test
    @DisplayName("드롭 시작 시각이 지난 첫 tick에는 Active로 전환하고 대기열 입장을 허용한다")
    void processQueue_JustAfterDropStart_ActivatesOnceAndAllowsEntries() {
        // given
        LocalDateTime now = LocalDateTime.now();
        given(todayDropCache.get()).willReturn(List.of(
                cachedDrop(dropId, now.minusMinutes(1), now.plusMinutes(30), false, false)
        ));

        // when
        queueScheduler.processQueue();

        // then
        verify(dropService).changeDropStatusActive(dropId);
        verify(queueManager).allowEntries(eq(dropId), anyInt());
        verify(queueManager, never()).finishDrop(any());
    }

    @Test
    @DisplayName("이미 Active 전환된 드롭은 tick마다 다시 전환하지 않지만 대기열 입장 허용은 계속한다")
    void processQueue_AlreadyStarted_DoesNotReactivate() {
        // given
        LocalDateTime now = LocalDateTime.now();
        given(todayDropCache.get()).willReturn(List.of(
                cachedDrop(dropId, now.minusMinutes(1), now.plusMinutes(30), true, false)
        ));

        // when
        queueScheduler.processQueue();

        // then
        verify(dropService, never()).changeDropStatusActive(any());
        verify(queueManager).allowEntries(eq(dropId), anyInt());
    }

    @Test
    @DisplayName("마감 시각이 지난 첫 tick에는 Completed로 전환하고 대기열을 정리한다")
    void processQueue_AfterDropEnd_CompletesOnceAndFinishesQueue() {
        // given
        LocalDateTime now = LocalDateTime.now();
        given(todayDropCache.get()).willReturn(List.of(
                cachedDrop(dropId, now.minusHours(2), now.minusMinutes(1), true, false)
        ));

        // when
        queueScheduler.processQueue();

        // then
        verify(dropService).changeDropStatusCompleted(dropId);
        verify(queueManager).finishDrop(dropId);
        verify(dropService, never()).changeDropStatusActive(any());
        verify(queueManager, never()).allowEntries(any(), anyInt());
    }

    @Test
    @DisplayName("이미 Completed 전환된 드롭은 tick마다 다시 전환하지 않지만 대기열 정리는 계속한다")
    void processQueue_AfterDropEnd_AlreadyCompleted_DoesNotCompleteAgain() {
        // given
        LocalDateTime now = LocalDateTime.now();
        given(todayDropCache.get()).willReturn(List.of(
                cachedDrop(dropId, now.minusHours(2), now.minusMinutes(1), true, true)
        ));

        // when
        queueScheduler.processQueue();

        // then
        verify(dropService, never()).changeDropStatusCompleted(any());
        verify(queueManager).finishDrop(dropId);
    }

    @Test
    @DisplayName("여러 드롭이 있을 때 앞쪽 드롭이 시작 전이어도 뒤쪽의 진행 중인 드롭은 정상 처리한다")
    void processQueue_MultipleDrops_ProcessesEachIndependently() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Long upcomingDropId = 2L;
        Long activeDropId = 3L;
        given(todayDropCache.get()).willReturn(List.of(
                cachedDrop(upcomingDropId, now.plusMinutes(10), now.plusHours(1), false, false),
                cachedDrop(activeDropId, now.minusMinutes(1), now.plusMinutes(30), false, false)
        ));

        // when
        queueScheduler.processQueue();

        // then
        verify(dropService, never()).changeDropStatusActive(upcomingDropId);
        verify(queueManager, never()).allowEntries(eq(upcomingDropId), anyInt());
        verify(dropService).changeDropStatusActive(activeDropId);
        verify(queueManager).allowEntries(eq(activeDropId), anyInt());
    }

    @Test
    @DisplayName("오늘 진행되는 드롭이 없으면 active 멤버 만료 체크를 하지 않는다")
    void checkActiveMembers_NoDropToday_DoesNothing() {
        // given
        given(todayDropCache.get()).willReturn(List.of());

        // when
        queueScheduler.checkActiveMembers();

        // then
        verify(queueManager, never()).checkActiveMembers(any());
        verify(dropEnterService, never()).failExpiredEntries(any(), any());
    }

    @Test
    @DisplayName("드롭 진행 시간이 아니면 active 멤버 만료 체크를 하지 않는다")
    void checkActiveMembers_OutsideWindow_DoesNothing() {
        // given
        LocalDateTime now = LocalDateTime.now();
        given(todayDropCache.get()).willReturn(List.of(
                cachedDrop(dropId, now.plusMinutes(10), now.plusHours(1), false, false)
        ));

        // when
        queueScheduler.checkActiveMembers();

        // then
        verify(queueManager, never()).checkActiveMembers(any());
        verify(dropEnterService, never()).failExpiredEntries(any(), any());
    }

    @Test
    @DisplayName("드롭 진행 중이면 active 멤버 만료 체크를 수행하고 만료된 멤버를 실패 처리로 넘긴다")
    void checkActiveMembers_InsideWindow_ChecksActiveMembers() {
        // given
        LocalDateTime now = LocalDateTime.now();
        given(todayDropCache.get()).willReturn(List.of(
                cachedDrop(dropId, now.minusMinutes(1), now.plusMinutes(30), true, false)
        ));
        Set<Long> expiredMemberIds = Set.of(10L, 20L);
        given(queueManager.checkActiveMembers(dropId)).willReturn(expiredMemberIds);

        // when
        queueScheduler.checkActiveMembers();

        // then
        verify(queueManager).checkActiveMembers(dropId);
        verify(dropEnterService).failExpiredEntries(dropId, expiredMemberIds);
    }

    @Test
    @DisplayName("만료된 active 멤버가 없어도(빈 Set) 예외 없이 실패 처리 호출로 넘어간다")
    void checkActiveMembers_NoExpiredMembers_PassesEmptySetWithoutError() {
        // given
        LocalDateTime now = LocalDateTime.now();
        given(todayDropCache.get()).willReturn(List.of(
                cachedDrop(dropId, now.minusMinutes(1), now.plusMinutes(30), true, false)
        ));
        given(queueManager.checkActiveMembers(dropId)).willReturn(Set.of());

        // when
        queueScheduler.checkActiveMembers();

        // then
        verify(dropEnterService).failExpiredEntries(dropId, Set.of());
    }

    @Test
    @DisplayName("드롭 시작 첫 tick에는 Active 전환과 함께 재고 카운터를 워밍업한다")
    void processQueue_JustAfterDropStart_WarmsUpStock() {
        // given
        LocalDateTime now = LocalDateTime.now();
        CachedDrop drop = cachedDrop(dropId, now.minusMinutes(1), now.plusMinutes(30), false, false);
        given(todayDropCache.get()).willReturn(List.of(drop));

        // when
        queueScheduler.processQueue();

        // then
        verify(dropStockSyncService).warmUp(drop);
    }

    @Test
    @DisplayName("이미 시작된 드롭은 tick마다 워밍업을 반복하지 않는다")
    void processQueue_AlreadyStarted_DoesNotWarmUpAgain() {
        // given
        LocalDateTime now = LocalDateTime.now();
        given(todayDropCache.get()).willReturn(List.of(
                cachedDrop(dropId, now.minusMinutes(1), now.plusMinutes(30), true, false)
        ));

        // when
        queueScheduler.processQueue();

        // then
        verify(dropStockSyncService, never()).warmUp(any());
    }

    @Test
    @DisplayName("마감 첫 tick에는 재고를 최종 확정한다")
    void processQueue_AfterDropEnd_FinalizesStockOnce() {
        // given
        LocalDateTime now = LocalDateTime.now();
        CachedDrop drop = cachedDrop(dropId, now.minusHours(2), now.minusMinutes(1), true, false);
        given(todayDropCache.get()).willReturn(List.of(drop));

        // when
        queueScheduler.processQueue();

        // then
        verify(dropStockSyncService).finalizeStock(drop);
    }

    @Test
    @DisplayName("이미 마감 처리된 드롭은 재고 확정을 반복하지 않는다")
    void processQueue_AlreadyEnded_DoesNotFinalizeAgain() {
        // given
        LocalDateTime now = LocalDateTime.now();
        given(todayDropCache.get()).willReturn(List.of(
                cachedDrop(dropId, now.minusHours(2), now.minusMinutes(1), true, true)
        ));

        // when
        queueScheduler.processQueue();

        // then
        verify(dropStockSyncService, never()).finalizeStock(any());
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
        queueScheduler.syncDropStock();

        // then
        verify(dropStockSyncService).sync(active);
        verify(dropStockSyncService, never()).sync(upcoming);
        verify(dropStockSyncService, never()).sync(ended);
    }

    @Test
    @DisplayName("서버 기동 시 오늘 드롭 캐시를 갱신한다")
    void init_RefreshesTodayDropCache() {
        // when
        queueScheduler.init();

        // then
        verify(todayDropCache).refresh();
    }

    @Test
    @DisplayName("자정 스케줄에서 오늘 드롭 캐시를 갱신한다")
    void refreshTodayDrop_RefreshesTodayDropCache() {
        // when
        queueScheduler.refreshTodayDrop();

        // then
        verify(todayDropCache).refresh();
    }
}
