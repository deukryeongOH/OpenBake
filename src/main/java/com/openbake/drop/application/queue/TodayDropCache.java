package com.openbake.drop.application.queue;

import com.openbake.drop.domain.Drop;
import com.openbake.drop.domain.DropRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

// 오늘 진행되는 드롭 정보를 캐싱한다. QueueScheduler가 매초 DB를 조회하지 않도록
// 자정/기동 시에 refresh 하며, 당일 드롭이 등록/수정/삭제될 때도 DropService가 refresh를 호출해
// 캐시가 자정 시점 스냅샷으로 굳어버리는 것을 막는다.
@Component
@RequiredArgsConstructor
public class TodayDropCache {

    private final DropRepository dropRepository;

    private final AtomicReference<CachedDrop> cachedDrop = new AtomicReference<>(CachedDrop.empty(LocalDate.now()));
    private final AtomicBoolean checkStart = new AtomicBoolean(false);
    private final AtomicBoolean checkEnd = new AtomicBoolean(false);

    public void refresh() {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.atTime(LocalTime.MAX);

        checkStart.set(false);
        checkEnd.set(false);

        Optional<Drop> findDrop = dropRepository.findByDropStartBetween(todayStart, todayEnd); // 하루에 드롭 하나라 가능

        cachedDrop.set(findDrop
                .map(drop -> new CachedDrop(today, drop.getId(), drop.getDropStart(), drop.getDropEnd()))
                .orElseGet(() -> CachedDrop.empty(today)));
    }

    public CachedDrop get() {
        return cachedDrop.get();
    }

    // true를 반환하는 최초 1회에만 Active 전환을 수행
    public boolean tryMarkStarted() {
        return checkStart.compareAndSet(false, true);
    }

    // true를 반환하는 최초 1회에만 Completed 전환을 수행
    public boolean tryMarkEnded() {
        return checkEnd.compareAndSet(false, true);
    }

    public record CachedDrop(LocalDate cachedDate, Long dropId, LocalDateTime dropStart, LocalDateTime dropEnd) {
        static CachedDrop empty(LocalDate date) {
            return new CachedDrop(date, null, null, null);
        }
    }
}