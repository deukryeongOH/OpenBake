package com.openbake.drop.application.cache;

import com.openbake.drop.domain.Drop;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

public record CachedDrop(LocalDate cachedDate, Long dropId, LocalDateTime dropStart, LocalDateTime dropEnd, AtomicBoolean checkStarted, AtomicBoolean checkEnded) {
    static CachedDrop of(Drop drop) {
        return new CachedDrop(LocalDate.now(), drop.getId(), drop.getDropStart(), drop.getDropEnd(), new AtomicBoolean(false), new AtomicBoolean(false));
    }
    // true를 반환하는 최초 1회에만 Active 전환을 수행
    public boolean tryMarkStarted() {
        return checkStarted.compareAndSet(false, true);
    }

    // true를 반환하는 최초 1회에만 Completed 전환을 수행
    public boolean tryMarkEnded() {
        return checkEnded.compareAndSet(false, true);
    }
}
