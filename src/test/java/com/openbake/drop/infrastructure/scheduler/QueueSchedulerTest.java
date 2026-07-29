package com.openbake.drop.infrastructure.scheduler;

import com.openbake.drop.application.DropEnterService;
import com.openbake.drop.application.DropService;
import com.openbake.drop.application.queue.InMemoryQueueManager;
import com.openbake.drop.application.queue.TodayDropCache;
import com.openbake.drop.application.queue.TodayDropCache.CachedDrop;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

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

    @InjectMocks
    private QueueScheduler queueScheduler;

    private final Long dropId = 1L;

    private static CachedDrop cachedDrop(Long dropId, LocalDateTime start, LocalDateTime end) {
        return new CachedDrop(LocalDate.now(), dropId, start, end);
    }

    @Test
    @DisplayName("오늘 진행되는 드롭이 없으면 아무 것도 하지 않는다")
    void processQueue_NoDropToday_DoesNothing() {
        // given
        given(todayDropCache.get()).willReturn(cachedDrop(null, null, null));

        // when
        queueScheduler.processQueue();

        // then
        verify(dropService, never()).changeDropStatusActive(any());
        verify(dropService, never()).changeDropStatusCompleted(any());
        verify(queueManager, never()).allowEntries(any(), anyInt());
        verify(queueManager, never()).finishDrop(any());
    }

    @Test
    @DisplayName("드롭 시작 전이면 상태 전환 없이 대기열만 정리한다")
    void processQueue_BeforeDropStart_OnlyFinishesQueue() {
        // given
        LocalDateTime now = LocalDateTime.now();
        given(todayDropCache.get()).willReturn(cachedDrop(dropId, now.plusMinutes(10), now.plusHours(1)));

        // when
        queueScheduler.processQueue();

        // then
        verify(queueManager).finishDrop(dropId);
        verify(dropService, never()).changeDropStatusActive(any());
        verify(todayDropCache, never()).tryMarkStarted();
    }

    @Test
    @DisplayName("드롭 시작 시각이 지난 첫 tick에는 Active로 전환하고 대기열 입장을 허용한다")
    void processQueue_JustAfterDropStart_ActivatesOnceAndAllowsEntries() {
        // given
        LocalDateTime now = LocalDateTime.now();
        given(todayDropCache.get()).willReturn(cachedDrop(dropId, now.minusMinutes(1), now.plusMinutes(30)));
        given(todayDropCache.tryMarkStarted()).willReturn(true);

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
        given(todayDropCache.get()).willReturn(cachedDrop(dropId, now.minusMinutes(1), now.plusMinutes(30)));
        given(todayDropCache.tryMarkStarted()).willReturn(false);

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
        given(todayDropCache.get()).willReturn(cachedDrop(dropId, now.minusHours(2), now.minusMinutes(1)));
        given(todayDropCache.tryMarkEnded()).willReturn(true);

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
        given(todayDropCache.get()).willReturn(cachedDrop(dropId, now.minusHours(2), now.minusMinutes(1)));
        given(todayDropCache.tryMarkEnded()).willReturn(false);

        // when
        queueScheduler.processQueue();

        // then
        verify(dropService, never()).changeDropStatusCompleted(any());
        verify(queueManager).finishDrop(dropId);
    }

    @Test
    @DisplayName("오늘 진행되는 드롭이 없으면 active 멤버 만료 체크를 하지 않는다")
    void checkActiveMembers_NoDropToday_DoesNothing() {
        // given
        given(todayDropCache.get()).willReturn(cachedDrop(null, null, null));

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
        given(todayDropCache.get()).willReturn(cachedDrop(dropId, now.plusMinutes(10), now.plusHours(1)));

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
        given(todayDropCache.get()).willReturn(cachedDrop(dropId, now.minusMinutes(1), now.plusMinutes(30)));
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
        given(todayDropCache.get()).willReturn(cachedDrop(dropId, now.minusMinutes(1), now.plusMinutes(30)));
        given(queueManager.checkActiveMembers(dropId)).willReturn(Set.of());

        // when
        queueScheduler.checkActiveMembers();

        // then
        verify(dropEnterService).failExpiredEntries(dropId, Set.of());
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