package com.openbake.drop.infrastructure.scheduler;

import com.openbake.drop.application.DropService;
import com.openbake.drop.application.queue.InMemoryQueueManager;
import com.openbake.drop.domain.Drop;
import com.openbake.drop.domain.DropRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Component
@RequiredArgsConstructor
public class QueueScheduler {

    private static final int ENTRIES_PER_TICK = 100;

    private final InMemoryQueueManager queueManager;
    private final DropRepository dropRepository;
    private final DropService dropService;

    // 오늘의 드롭 정보 캐시. 매초 갱신하지 않고 자정/기동 시에만 DB를 조회한다.
    private final AtomicReference<CachedDrop> cachedDrop = new AtomicReference<>(CachedDrop.empty(LocalDate.now()));
    private final AtomicBoolean checkStart = new AtomicBoolean(false);;
    private final AtomicBoolean checkEnd = new AtomicBoolean(false);

    // 서버 기동 시 당일 드롭 정보를 1회 캐싱 (자정 스케줄을 못 탄 채로 기동될 수 있으므로)
    @PostConstruct
    void init() {
        refreshTodayDrop();
    }

    // 매일 자정에 1회만 DB를 조회해 오늘의 드롭 정보를 캐싱
    @Scheduled(cron = "0 0 0 * * *")
    public void refreshTodayDrop() {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.atTime(LocalTime.MAX);

        checkStart.set(false);
        checkEnd.set(false);

        Optional<Drop> findDrop = dropRepository.findByDropStartBetween(todayStart, todayEnd);

        cachedDrop.set(findDrop
                .map(drop -> new CachedDrop(today, drop.getId(), drop.getDropStart(), drop.getDropEnd()))
                .orElseGet(() -> CachedDrop.empty(today)));
    }

    // 1초마다 실행되지만 DB에는 접근하지 않고, 캐싱된 시간 정보로만 진행 중 여부를 판단한다.
    @Scheduled(fixedRate = 1000)
    public void processQueue() {
        CachedDrop drop = cachedDrop.get();

        if (drop.dropId() == null) {
            return; // 오늘 진행되는 드롭이 없음 (정상 상태)
        }

        if ((LocalDateTime.now().isAfter(drop.dropEnd) && checkEnd.compareAndSet(false, true))) {
            dropService.changeDropStatusCompleted(drop.dropId);
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(drop.dropStart()) || now.isAfter(drop.dropEnd())) {
            queueManager.finishDrop(drop.dropId);
            return; // 드롭 진행 시간이 아님
        }

        if (LocalDateTime.now().isAfter(drop.dropStart) && checkStart.compareAndSet(false, true)) {
            dropService.changeDropStatusActive(drop.dropId);
        }

        queueManager.allowEntries(drop.dropId(), ENTRIES_PER_TICK);
    }

    // 대기열은 통과했지만 수량을 선택하고 장바구니로 넘어가지 않는 Member 지속적으로 만료
    @Scheduled(fixedRate = 120000)
    public void checkActiveMembers() {
        CachedDrop drop = cachedDrop.get();

        if (drop.dropId() == null) {
            return; // 오늘 진행되는 드롭이 없음 (정상 상태)
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(drop.dropStart()) || now.isAfter(drop.dropEnd())) {
            return; // 드롭 진행 시간이 아님
        }

        queueManager.checkActiveMembers(drop.dropId);
    }
    private record CachedDrop(LocalDate cachedDate, Long dropId, LocalDateTime dropStart, LocalDateTime dropEnd) {
        static CachedDrop empty(LocalDate date) {
            return new CachedDrop(date, null, null, null);
        }
    }
}
