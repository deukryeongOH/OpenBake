package com.openbake.drop.application.cache;

import com.openbake.drop.domain.DropRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

// 오늘 진행되는 드롭 정보를 캐싱한다. QueueScheduler가 매초 DB를 조회하지 않도록
// 자정/기동 시에 refresh 하며, 당일 드롭이 등록/수정/삭제될 때도 DropService가 refresh를 호출해
// 캐시가 자정 시점 스냅샷으로 굳어버리는 것을 막는다.
@Component
@RequiredArgsConstructor
public class TodayDropCache {

    private final DropRepository dropRepository;

    private final AtomicReference<List<CachedDrop>> cachedDrop = new AtomicReference<>(List.of());

    public void refresh() {
        LocalDate today = LocalDate.now();
        List<CachedDrop> dropList = dropRepository.findListByDropDate(today).stream()
                .map(CachedDrop::of).toList();

        cachedDrop.set(dropList);
    }

    public List<CachedDrop> get() {
        return cachedDrop.get();
    }

}