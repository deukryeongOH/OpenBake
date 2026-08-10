package com.openbake.drop.infrastructure.scheduler;

import com.openbake.drop.application.DropEnterService;
import com.openbake.drop.application.DropService;
import com.openbake.drop.application.queue.QueueManager;
import com.openbake.drop.application.queue.TodayDropCache;
import com.openbake.drop.application.queue.TodayDropCache.CachedDrop;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class QueueScheduler {

    private static final int ENTRIES_PER_TICK = 100;

    private final QueueManager queueManager;
    private final DropService dropService;
    private final DropEnterService dropEnterService;
    private final TodayDropCache todayDropCache;

    // 서버 기동 시 당일 드롭 정보를 1회 캐싱 (자정 스케줄을 못 탄 채로 기동될 수 있으므로)
    @PostConstruct
    void init() {
        todayDropCache.refresh();
    }

    // 매일 자정에 1회만 DB를 조회해 오늘의 드롭 정보를 캐싱
    @Scheduled(cron = "0 0 0 * * *")
    public void refreshTodayDrop() {
        todayDropCache.refresh();
    }

    // 1초마다 실행되지만 DB에는 접근하지 않고, 캐싱된 시간 정보로만 진행 중 여부를 판단한다.
    @Scheduled(fixedRate = 1000)
    public void processQueue() {
        CachedDrop drop = todayDropCache.get();

        if (drop.dropId() == null) {
            return; // 오늘 진행되는 드롭이 없음 (정상 상태)
        }

        if (LocalDateTime.now().isAfter(drop.dropEnd()) && todayDropCache.tryMarkEnded()) {
            dropService.changeDropStatusCompleted(drop.dropId());
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(drop.dropStart()) || now.isAfter(drop.dropEnd())) {
            queueManager.finishDrop(drop.dropId());
            return; // 드롭 진행 시간이 아님
        }

        if (LocalDateTime.now().isAfter(drop.dropStart()) && todayDropCache.tryMarkStarted()) {
            dropService.changeDropStatusActive(drop.dropId());
        }

        queueManager.allowEntries(drop.dropId(), ENTRIES_PER_TICK);
    }

    // 대기열은 통과했지만 수량을 선택하고 장바구니로 넘어가지 않는 Member 지속적으로 만료
    @Scheduled(fixedRate = 120000)
    public void checkActiveMembers() {
        CachedDrop drop = todayDropCache.get();

        if (drop.dropId() == null) {
            return; // 오늘 진행되는 드롭이 없음 (정상 상태)
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(drop.dropStart()) || now.isAfter(drop.dropEnd())) {
            return; // 드롭 진행 시간이 아님
        }

        Set<Long> memberSet = queueManager.checkActiveMembers(drop.dropId());
        dropEnterService.failExpiredEntries(drop.dropId(), memberSet);
    }
}
